package com.leaping.portfolio_app.market.service;


import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import com.leaping.portfolio_app.market.dto.QuoteDto;
import com.leaping.portfolio_app.market.dto.TradabilityDto;
import com.leaping.portfolio_app.market.entity.Price;
import com.leaping.portfolio_app.market.enums.TradabilityReason;
import com.leaping.portfolio_app.repository.InstrumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class PreTradeMarketService {

    private final InstrumentRepository instrumentRepository;
    private final PricingService pricingService;
    private final MarketHoursService marketHoursService;

    @Value("${market.quote.max-age.stock-minutes:4320}")
    private long stockQuoteMaxAgeMinutes;

    @Value("${market.quote.max-age.forex-minutes:4320}")
    private long forexQuoteMaxAgeMinutes;

    @Value("${market.quote.max-age.crypto-minutes:4320}")
    private long cryptoQuoteMaxAgeMinutes;

    public PreTradeMarketService(
            InstrumentRepository instrumentRepository,
            PricingService pricingService,
            MarketHoursService marketHoursService
    ) {
        this.instrumentRepository = instrumentRepository;
        this.pricingService = pricingService;
        this.marketHoursService = marketHoursService;
    }

    public Optional<QuoteDto> getQuote(String symbol) {
        Optional<Instrument> instrumentOpt = instrumentRepository.findBySymbolIgnoreCase(symbol);
        if (instrumentOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<Price> latestPriceOpt = pricingService.getLatestPriceBySymbol(symbol);
        if (latestPriceOpt.isEmpty()) {
            return Optional.empty();
        }

        Instrument instrument = instrumentOpt.get();
        Price latestPrice = latestPriceOpt.get();
        boolean stale = isPriceStale(instrument, latestPrice);
        long ageSeconds = Duration.between(latestPrice.getPriceTimestamp(), OffsetDateTime.now()).toSeconds();

        return Optional.of(new QuoteDto(
                instrument.getSymbol(),
                latestPrice.getPrice(),
                latestPrice.getPriceCurrency().getCurrencyCode(),
                latestPrice.getPriceTimestamp(),
                latestPrice.getSourceType().name(),
                latestPrice.getProviderName(),
                stale,
                Math.max(ageSeconds, 0)
        ));
    }

    public TradabilityDto getTradability(String symbol) {
        Optional<Instrument> instrumentOpt = instrumentRepository.findBySymbolIgnoreCase(symbol);
        if (instrumentOpt.isEmpty()) {
            return new TradabilityDto(
                    symbol,
                    false,
                    TradabilityReason.INSTRUMENT_NOT_FOUND,
                    false,
                    false,
                    false,
                    false,
                    "Instrument was not found"
            );
        }

        Instrument instrument = instrumentOpt.get();

        if (!Boolean.TRUE.equals(instrument.getIsActive())) {
            return new TradabilityDto(
                    instrument.getSymbol(),
                    false,
                    TradabilityReason.INSTRUMENT_INACTIVE,
                    false,
                    false,
                    false,
                    false,
                    "Instrument is inactive"
            );
        }

        if (!Boolean.TRUE.equals(instrument.getIsTradeable())) {
            return new TradabilityDto(
                    instrument.getSymbol(),
                    false,
                    TradabilityReason.INSTRUMENT_NOT_TRADEABLE,
                    false,
                    false,
                    false,
                    false,
                    "Instrument is marked as not tradeable"
            );
        }

        MarketStatus marketStatus = marketHoursService.getMarketStatus(instrument.getMarket());
        if (!marketStatus.isAlwaysOpen() && !marketStatus.isOpen()) {
            return new TradabilityDto(
                    instrument.getSymbol(),
                    false,
                    TradabilityReason.MARKET_CLOSED,
                    false,
                    false,
                    false,
                    false,
                    "Market is currently closed"
            );
        }

        Optional<Price> latestPriceOpt = pricingService.getLatestPriceBySymbol(instrument.getSymbol());
        if (latestPriceOpt.isEmpty()) {
            return new TradabilityDto(
                    instrument.getSymbol(),
                    false,
                    TradabilityReason.PRICE_UNAVAILABLE,
                    marketStatus.isOpen(),
                    marketStatus.isAlwaysOpen(),
                    false,
                    false,
                    "No quote available for instrument"
            );
        }

        boolean stale = isPriceStale(instrument, latestPriceOpt.get());
        if (stale) {
            return new TradabilityDto(
                    instrument.getSymbol(),
                    false,
                    TradabilityReason.PRICE_STALE,
                    marketStatus.isOpen(),
                    marketStatus.isAlwaysOpen(),
                    true,
                    true,
                    "Latest quote is stale"
            );
        }

        return new TradabilityDto(
                instrument.getSymbol(),
                true,
                TradabilityReason.TRADABLE,
                marketStatus.isOpen(),
                marketStatus.isAlwaysOpen(),
                true,
                false,
                "Instrument is tradable"
        );
    }

    private boolean isPriceStale(Instrument instrument, Price price) {
        long maxAgeMinutes = getMaxAgeMinutes(instrument.getInstrumentType());
        Duration age = Duration.between(price.getPriceTimestamp(), OffsetDateTime.now());
        return age.toMinutes() > maxAgeMinutes;
    }

    private long getMaxAgeMinutes(InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.FOREX) {
            return forexQuoteMaxAgeMinutes;
        }
        if (instrumentType == InstrumentType.CRYPTO) {
            return cryptoQuoteMaxAgeMinutes;
        }
        return stockQuoteMaxAgeMinutes;
    }
}
