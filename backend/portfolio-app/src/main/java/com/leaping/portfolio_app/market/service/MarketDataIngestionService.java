package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.entity.Price;
import com.leaping.portfolio_app.market.enums.PriceSourceType;
import com.leaping.portfolio_app.market.repository.PriceRepository;
import com.leaping.portfolio_app.repository.InstrumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class MarketDataIngestionService {

    private static final String PROVIDER = "yahoo-historical";

    private final InstrumentRepository instrumentRepository;
    private final PriceRepository priceRepository;
    private final YahooHistoricalPriceProvider yahooHistoricalPriceProvider;
    private final SymbolMapper symbolMapper;

    @Value("${market.pricing.history-days:30}")
    private int historyDays;

    public MarketDataIngestionService(
            InstrumentRepository instrumentRepository,
            PriceRepository priceRepository,
            YahooHistoricalPriceProvider yahooHistoricalPriceProvider,
            SymbolMapper symbolMapper
    ) {
        this.instrumentRepository = instrumentRepository;
        this.priceRepository = priceRepository;
        this.yahooHistoricalPriceProvider = yahooHistoricalPriceProvider;
        this.symbolMapper = symbolMapper;
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
        List<Instrument> instruments = instrumentRepository.findByIsActiveTrueAndIsTradeableTrue();
        if (instruments.isEmpty()) {
            return;
        }

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(historyDays);

        for (Instrument instrument : instruments) {
            String yahooSymbol = symbolMapper.toYahooSymbol(instrument);
            List<MarketPricePoint> points = yahooHistoricalPriceProvider.getHistoricalClosePrices(
                    yahooSymbol,
                    from,
                    to
            );

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
}
