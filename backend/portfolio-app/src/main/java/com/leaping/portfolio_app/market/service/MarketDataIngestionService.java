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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class MarketDataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataIngestionService.class);
    private static final String HISTORY_PROVIDER = "csv-universe-mock";
    private final Object liveTickLock = new Object();

    private final InstrumentRepository instrumentRepository;
    private final PriceRepository priceRepository;
    private final MarketRepository marketRepository;
    private final CurrencyRepository currencyRepository;
    private final ResourceLoader resourceLoader;
    private final MarketHoursService marketHoursService;
    private final PriceMovementSimulator priceMovementSimulator;
    private final TransactionTemplate transactionTemplate;

    @Value("${market.pricing.history-days:30}")
    private int historyDays;

    @Value("${market.pricing.startup-backfill-enabled:true}")
    private boolean startupBackfillEnabled = true;

    @Value("${market.universe.csv-path:classpath:data/mock_instrument_universe.csv}")
    private String universeCsvPath;

    @Value("${market.simulation.enabled:true}")
    private boolean simulationEnabled;

    @Value("${market.simulation.tick-ms:30000}")
    private long tickMs;

    @Value("${market.simulation.respect-market-hours:true}")
    private boolean respectMarketHours;

    public MarketDataIngestionService(
            InstrumentRepository instrumentRepository,
            PriceRepository priceRepository,
            MarketRepository marketRepository,
            CurrencyRepository currencyRepository,
            ResourceLoader resourceLoader,
            MarketHoursService marketHoursService,
            PriceMovementSimulator priceMovementSimulator,
            PlatformTransactionManager transactionManager
    ) {
        this.instrumentRepository = instrumentRepository;
        this.priceRepository = priceRepository;
        this.marketRepository = marketRepository;
        this.currencyRepository = currencyRepository;
        this.resourceLoader = resourceLoader;
        this.marketHoursService = marketHoursService;
        this.priceMovementSimulator = priceMovementSimulator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialBackfillOnStartup() {
        if (!startupBackfillEnabled) {
            return;
        }

        initialBackfill();
    }

    public void initialBackfill() {
        List<UniverseRow> universe = loadUniverseRows();
        if (universe.isEmpty()) {
            return;
        }

        // Lock first, then open the transaction. Startup and the scheduler share
        // liveTickLock; taking a database connection before the lock deadlocks
        // when backfill still holds row locks and the scheduler holds the lock.
        synchronized (liveTickLock) {
            transactionTemplate.executeWithoutResult(status -> {
                // Daily history is written for every universe row, including an equity
                // whose exchange is closed on this first start. Live ticks stay inside
                // market hours, so that opening price does not move until the session opens.
                backfillHistoricalPrices(universe);
                if (simulationEnabled) {
                    appendLiveTicksLocked(universe);
                }
            });
        }
    }

    @Scheduled(
            initialDelayString = "${market.pricing.refresh-ms:30000}",
            fixedDelayString = "${market.pricing.refresh-ms:30000}"
    )
    public void scheduledRefresh() {
        List<UniverseRow> universe = loadUniverseRows();
        if (universe.isEmpty()) {
            return;
        }

        appendLiveTicks(universe);
    }

    private void backfillHistoricalPrices(List<UniverseRow> universe) {
        // Stop at yesterday. Daily bars are only the backdrop before the walk
        // starts; a bar on top of an existing live print is removed so the
        // quote and the next step stay on price-simulator.
        LocalDate to = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        int days = Math.max(historyDays, 1);
        LocalDate from = to.minusDays(days - 1L);
        int removedOverlays = 0;

        for (UniverseRow row : universe) {
            Instrument instrument = upsertInstrument(row);
            if (instrument == null) {
                continue;
            }
            removedOverlays += removeHistoryOnTopOfLiveWalk(instrument);
            OffsetDateTime firstLiveTick = priceRepository
                    .findFirstByInstrumentAndProviderNameOrderByPriceTimestampAsc(
                            instrument,
                            PriceMovementSimulator.PROVIDER
                    )
                    .map(Price::getPriceTimestamp)
                    .orElse(null);
            List<MarketPricePoint> points = generateMockHistory(row, from, to);

            for (MarketPricePoint point : points) {
                if (firstLiveTick != null && !point.getTimestamp().isBefore(firstLiveTick)) {
                    continue;
                }
                boolean alreadyExists = priceRepository.existsByInstrumentAndPriceTimestampAndProviderName(
                        instrument,
                        point.getTimestamp(),
                        HISTORY_PROVIDER
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
                        HISTORY_PROVIDER
                );
                priceRepository.save(price);
            }
        }
        if (removedOverlays > 0) {
            log.info("Removed {} historical mock prices that were newer than the live walk", removedOverlays);
        }
        priceRepository.flush();
    }

    private int removeHistoryOnTopOfLiveWalk(Instrument instrument) {
        Optional<Price> firstLive = priceRepository
                .findFirstByInstrumentAndProviderNameOrderByPriceTimestampAsc(
                        instrument,
                        PriceMovementSimulator.PROVIDER
                );
        if (firstLive.isEmpty()) {
            return 0;
        }

        List<Price> overlays = priceRepository.findByInstrumentAndProviderNameAndPriceTimestampGreaterThanEqual(
                instrument,
                HISTORY_PROVIDER,
                firstLive.get().getPriceTimestamp()
        );
        if (overlays.isEmpty()) {
            return 0;
        }
        priceRepository.deleteAll(overlays);
        return overlays.size();
    }

    private void appendLiveTicks(List<UniverseRow> universe) {
        if (!simulationEnabled) {
            return;
        }
        synchronized (liveTickLock) {
            transactionTemplate.executeWithoutResult(status -> appendLiveTicksLocked(universe));
        }
    }

    private void appendLiveTicksLocked(List<UniverseRow> universe) {
        long safeTickMs = Math.max(1000L, tickMs);
        long tickSeconds = safeTickMs / 1000L;
        long bucketEpoch = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() / tickSeconds * tickSeconds;
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(Instant.ofEpochSecond(bucketEpoch), ZoneOffset.UTC);
        int inserted = 0;

        for (UniverseRow row : universe) {
            Instrument instrument = upsertInstrument(row);
            if (instrument == null) {
                continue;
            }
            if (!marketAcceptsTick(instrument)) {
                seedOpeningPriceWhenClosed(instrument, row, timestamp);
                continue;
            }

            boolean alreadyExists = priceRepository.existsByInstrumentAndPriceTimestampAndProviderName(
                    instrument,
                    timestamp,
                    PriceMovementSimulator.PROVIDER
            );
            if (alreadyExists) {
                continue;
            }

            BigDecimal lastPrice = latestWalkPrice(instrument);

            BigDecimal simulatedPrice = priceMovementSimulator.nextPrice(
                    row.symbol(),
                    row.instrumentType(),
                    lastPrice,
                    row.basePrice(),
                    row.dailyVolatilityBps(),
                    timestamp,
                    safeTickMs
            );

            Price tick = new Price(
                    instrument,
                    simulatedPrice,
                    instrument.getPriceCurrency(),
                    timestamp,
                    PriceSourceType.MOCK,
                    PriceMovementSimulator.PROVIDER
            );
            priceRepository.save(tick);
            inserted++;
        }

        if (inserted > 0) {
            log.debug("Appended {} simulated prices at {}", inserted, timestamp);
        }
    }

    /**
     * A closed market still needs a quote the first time it is seen.
     * Later passes leave that print unchanged until the exchange is open.
     */
    private void seedOpeningPriceWhenClosed(Instrument instrument, UniverseRow row, OffsetDateTime timestamp) {
        if (priceRepository.findFirstByInstrumentSymbolIgnoreCaseOrderByPriceTimestampDesc(instrument.getSymbol()).isPresent()) {
            return;
        }
        if (priceRepository.existsByInstrumentAndPriceTimestampAndProviderName(
                instrument,
                timestamp,
                PriceMovementSimulator.PROVIDER
        )) {
            return;
        }

        BigDecimal opening = row.basePrice();
        if (opening == null || opening.signum() <= 0) {
            opening = new BigDecimal("0.0001");
        }
        opening = opening.setScale(8, RoundingMode.HALF_UP);
        priceRepository.save(new Price(
                instrument,
                opening,
                instrument.getPriceCurrency(),
                timestamp,
                PriceSourceType.MOCK,
                PriceMovementSimulator.PROVIDER
        ));
    }

    private BigDecimal latestWalkPrice(Instrument instrument) {
        Optional<Price> live = priceRepository.findFirstByInstrumentAndProviderNameOrderByPriceTimestampDesc(
                instrument,
                PriceMovementSimulator.PROVIDER
        );
        if (live.isPresent()) {
            return live.get().getPrice();
        }
        return priceRepository
                .findFirstByInstrumentSymbolIgnoreCaseOrderByPriceTimestampDesc(instrument.getSymbol())
                .map(Price::getPrice)
                .orElse(null);
    }

    private boolean marketAcceptsTick(Instrument instrument) {
        if (!respectMarketHours || instrument.getInstrumentType() != InstrumentType.STOCK) {
            return true;
        }
        Market market = instrument.getMarket();
        if (market == null) {
            return false;
        }
        return marketHoursService.getMarketStatus(market).isOpen();
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
