package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.market.entity.Price;
import com.leaping.portfolio_app.market.enums.PriceSourceType;
import com.leaping.portfolio_app.market.repository.CurrencyRepository;
import com.leaping.portfolio_app.market.repository.MarketRepository;
import com.leaping.portfolio_app.market.repository.PriceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MarketDataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataIngestionService.class);
    private static final String PROVIDER = "csv-universe-mock";

    private final InstrumentRepository instrumentRepository;
    private final PriceRepository priceRepository;
    private final MarketRepository marketRepository;
    private final CurrencyRepository currencyRepository;
    private final ResourceLoader resourceLoader;

    @Value("${market.pricing.history-days:30}")
    private int historyDays;

    @Value("${market.universe.csv-path:classpath:data/mock_instrument_universe.csv}")
    private String universeCsvPath;

    public MarketDataIngestionService(
            InstrumentRepository instrumentRepository,
            PriceRepository priceRepository,
            MarketRepository marketRepository,
            CurrencyRepository currencyRepository,
            ResourceLoader resourceLoader
    ) {
        this.instrumentRepository = instrumentRepository;
        this.priceRepository = priceRepository;
        this.marketRepository = marketRepository;
        this.currencyRepository = currencyRepository;
        this.resourceLoader = resourceLoader;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialBackfill() {
        syncHistoricalPrices();
    }

    @Scheduled(fixedDelayString = "${market.pricing.refresh-ms:900000}")
    @Transactional
    public void scheduledRefresh() {
        syncHistoricalPrices();
    }

    private void syncHistoricalPrices() {
        List<UniverseRow> universe = loadUniverseRows();
        if (universe.isEmpty()) {
            return;
        }

        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minusDays(historyDays);

        for (UniverseRow row : universe) {
            Instrument instrument = upsertInstrument(row);
            if (instrument == null) {
                continue;
            }
            List<MarketPricePoint> points = generateMockHistory(row, from, to);

            for (MarketPricePoint point : points) {
                boolean alreadyExists = priceRepository.existsByInstrumentAndPriceTimestampAndProviderName(
                        instrument,
                        point.getTimestamp(),
                        PROVIDER
                );
                if (alreadyExists) {
                    continue;
                }

                Price price = new Price(
                        instrument,
                        point.getPrice(),
                        instrument.getPriceCurrency(),
                        point.getTimestamp(),
                        PriceSourceType.MOCK,
                        PROVIDER
                );
                priceRepository.save(price);
            }
        }
    }

    private List<UniverseRow> loadUniverseRows() {
        Resource resource = resourceLoader.getResource(universeCsvPath);
        if (!resource.exists()) {
            log.warn("Market universe CSV not found at {}", universeCsvPath);
            return List.of();
        }

        List<UniverseRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (lineNo == 1 || trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split(",", -1);
                if (parts.length != 11) {
                    log.warn("Skipping malformed universe row {}: {}", lineNo, line);
                    continue;
                }

                try {
                    rows.add(new UniverseRow(
                            normalize(parts[0]),
                            normalize(parts[1]),
                            normalize(parts[2]),
                            normalize(parts[3]),
                            normalize(parts[4]),
                            InstrumentType.valueOf(normalize(parts[5]).toUpperCase(Locale.ROOT)),
                            normalize(parts[6]).toUpperCase(Locale.ROOT),
                            new BigDecimal(normalize(parts[7])),
                            Integer.parseInt(normalize(parts[8])),
                            Boolean.parseBoolean(normalize(parts[9])),
                            Boolean.parseBoolean(normalize(parts[10]))
                    ));
                } catch (RuntimeException ex) {
                    log.warn("Skipping invalid universe row {}: {}", lineNo, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            log.error("Failed to read market universe CSV at {}", universeCsvPath, ex);
            return List.of();
        }

        return rows;
    }

    private Instrument upsertInstrument(UniverseRow row) {
        Market market = marketRepository.findByMarketNameIgnoreCase(row.marketName()).orElse(null);
        if (market == null) {
            log.warn("Skipping universe symbol {} due to missing market {}", row.symbol(), row.marketName());
            return null;
        }

        Currency currency = currencyRepository.findById(row.priceCurrencyCode()).orElse(null);
        if (currency == null) {
            log.warn("Skipping universe symbol {} due to missing currency {}", row.symbol(), row.priceCurrencyCode());
            return null;
        }

        Instrument instrument = instrumentRepository.findBySymbolIgnoreCase(row.symbol())
                .orElseGet(Instrument::new);

        instrument.setMarket(market);
        instrument.setSymbol(row.symbol());
        instrument.setName(row.name());
        instrument.setInstrumentType(row.instrumentType());
        instrument.setPriceCurrency(currency);
        instrument.setIsTradeable(row.isTradeable());
        instrument.setIsActive(row.isActive());

        return instrumentRepository.save(instrument);
    }

    private List<MarketPricePoint> generateMockHistory(UniverseRow row, LocalDate from, LocalDate to) {
        List<MarketPricePoint> points = new ArrayList<>();
        long totalDays = ChronoUnit.DAYS.between(from, to);
        long hash = Math.abs((long) row.symbol().hashCode());

        for (long day = 0; day <= totalDays; day++) {
            LocalDate date = from.plusDays(day);
            double wave = Math.sin((day + (hash % 17)) / 3.5d) * (row.dailyVolatilityBps() / 10_000d);
            double trend = (day / Math.max((double) totalDays, 1d)) * 0.035d;
            BigDecimal multiplier = BigDecimal.valueOf(1d + trend + wave);

            BigDecimal price = row.basePrice()
                    .multiply(multiplier)
                    .setScale(8, RoundingMode.HALF_UP);

            if (price.signum() <= 0) {
                price = row.basePrice().max(new BigDecimal("0.0001"));
            }

            OffsetDateTime timestamp = date.atTime(16, 0).atOffset(ZoneOffset.UTC);
            points.add(new MarketPricePoint(timestamp, price));
        }

        return points;
    }

    private String normalize(String value) {
        return StringUtils.trimWhitespace(value);
    }

    private record UniverseRow(
            String region,
            String assetClass,
            String marketName,
            String symbol,
            String name,
            InstrumentType instrumentType,
            String priceCurrencyCode,
            BigDecimal basePrice,
            int dailyVolatilityBps,
            boolean isTradeable,
            boolean isActive
    ) {
    }
}
