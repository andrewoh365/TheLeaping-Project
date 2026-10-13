package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.market.entity.Price;
import com.leaping.portfolio_app.market.repository.CurrencyRepository;
import com.leaping.portfolio_app.market.repository.MarketRepository;
import com.leaping.portfolio_app.market.repository.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataIngestionServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private PriceRepository priceRepository;

    @Mock
    private MarketRepository marketRepository;

    @Mock
    private CurrencyRepository currencyRepository;

    private final List<Price> prices = new ArrayList<>();
    private final Map<String, Instrument> instruments = new HashMap<>();
    private final MarketHoursService marketHoursService = new MarketHoursService();
    private MarketDataIngestionService service;

    @BeforeEach
    void setUp() {
        prices.clear();
        instruments.clear();
        stubReferences();
        stubPrices();

        service = new MarketDataIngestionService(
                instrumentRepository,
                priceRepository,
                marketRepository,
                currencyRepository,
                new DefaultResourceLoader(),
                marketHoursService,
                new PriceMovementSimulator(0.05d, 0.02d)
        );
        ReflectionTestUtils.setField(service, "historyDays", 2);
        ReflectionTestUtils.setField(service, "universeCsvPath", "classpath:data/closed-market-universe.csv");
        ReflectionTestUtils.setField(service, "simulationEnabled", true);
        ReflectionTestUtils.setField(service, "tickMs", 30_000L);
        ReflectionTestUtils.setField(service, "respectMarketHours", true);
    }

    @Test
    void firstStartOutsideLocalHours_storesAPriceAndDoesNotMoveIt() {
        assertFalse(marketHoursService.getMarketStatus(market("LSE")).isOpen());
        assertFalse(marketHoursService.getMarketStatus(market("NSE")).isOpen());

        service.initialBackfill();

        List<Price> vodBefore = pricesFor("VOD");
        List<Price> relianceBefore = pricesFor("RELIANCE");
        assertFalse(vodBefore.isEmpty());
        assertFalse(relianceBefore.isEmpty());
        assertTrue(vodBefore.stream().noneMatch(price -> "price-simulator".equals(price.getProviderName())));
        assertTrue(relianceBefore.stream().noneMatch(price -> "price-simulator".equals(price.getProviderName())));
        assertTrue(pricesFor("BTC-USD").stream().anyMatch(price -> "price-simulator".equals(price.getProviderName())));

        Price vodQuote = latest("VOD");
        Price relianceQuote = latest("RELIANCE");

        service.scheduledRefresh();

        assertEquals(vodBefore.size(), pricesFor("VOD").size());
        assertEquals(relianceBefore.size(), pricesFor("RELIANCE").size());
        assertEquals(0, vodQuote.getPrice().compareTo(latest("VOD").getPrice()));
        assertEquals(vodQuote.getPriceTimestamp(), latest("VOD").getPriceTimestamp());
        assertEquals(0, relianceQuote.getPrice().compareTo(latest("RELIANCE").getPrice()));
        assertEquals(relianceQuote.getPriceTimestamp(), latest("RELIANCE").getPriceTimestamp());
    }

    @Test
    void closedMarketWithNoHistory_seedsOneOpeningPriceAndLeavesIt() {
        service.scheduledRefresh();

        assertEquals(1, pricesFor("VOD").size());
        assertEquals(1, pricesFor("RELIANCE").size());
        assertEquals(0, new BigDecimal("176.90000000").compareTo(latest("VOD").getPrice()));
        assertEquals(0, new BigDecimal("842.34000000").compareTo(latest("RELIANCE").getPrice()));
        assertEquals(PriceMovementSimulator.PROVIDER, latest("VOD").getProviderName());
        assertEquals(PriceMovementSimulator.PROVIDER, latest("RELIANCE").getProviderName());

        service.scheduledRefresh();

        assertEquals(1, pricesFor("VOD").size());
        assertEquals(1, pricesFor("RELIANCE").size());
        assertEquals(0, new BigDecimal("176.90000000").compareTo(latest("VOD").getPrice()));
        assertEquals(0, new BigDecimal("842.34000000").compareTo(latest("RELIANCE").getPrice()));
    }

    private void stubReferences() {
        lenient().when(marketRepository.findByMarketNameIgnoreCase(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            if ("CRYPTO".equalsIgnoreCase(name)) {
                return Optional.of(new Market("CRYPTO", "Global", "UTC", null, null));
            }
            return Optional.of(market(name));
        });
        lenient().when(currencyRepository.findById(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            Currency currency = new Currency();
            currency.setCurrencyCode(code);
            currency.setCurrencyName(code);
            currency.setCurrentExchangeRateToUsd(BigDecimal.ONE);
            return Optional.of(currency);
        });
        lenient().when(instrumentRepository.findBySymbolIgnoreCase(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(instruments.get(invocation.getArgument(0).toString().toUpperCase(Locale.ROOT)))
        );
        lenient().when(instrumentRepository.save(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument instrument = invocation.getArgument(0);
            instruments.put(instrument.getSymbol().toUpperCase(Locale.ROOT), instrument);
            return instrument;
        });
    }

    private void stubPrices() {
        lenient().when(priceRepository.save(any(Price.class))).thenAnswer(invocation -> {
            Price price = invocation.getArgument(0);
            prices.add(price);
            return price;
        });
        lenient().when(priceRepository.existsByInstrumentAndPriceTimestampAndProviderName(any(), any(), anyString()))
                .thenAnswer(invocation -> prices.stream().anyMatch(price ->
                        price.getInstrument() == invocation.getArgument(0)
                                && price.getPriceTimestamp().equals(invocation.getArgument(1))
                                && price.getProviderName().equals(invocation.getArgument(2))
                ));
        lenient().when(priceRepository.findFirstByInstrumentSymbolIgnoreCaseOrderByPriceTimestampDesc(anyString()))
                .thenAnswer(invocation -> latestOptional(invocation.getArgument(0)));
        lenient().when(priceRepository.findFirstByInstrumentAndProviderNameOrderByPriceTimestampAsc(any(), anyString()))
                .thenAnswer(invocation -> prices.stream()
                        .filter(price -> price.getInstrument() == invocation.getArgument(0)
                                && price.getProviderName().equals(invocation.getArgument(1)))
                        .min((left, right) -> left.getPriceTimestamp().compareTo(right.getPriceTimestamp()))
                );
        lenient().when(priceRepository.findFirstByInstrumentAndProviderNameOrderByPriceTimestampDesc(any(), anyString()))
                .thenAnswer(invocation -> prices.stream()
                        .filter(price -> price.getInstrument() == invocation.getArgument(0)
                                && price.getProviderName().equals(invocation.getArgument(1)))
                        .max((left, right) -> left.getPriceTimestamp().compareTo(right.getPriceTimestamp()))
                );
        lenient().when(priceRepository.findByInstrumentAndProviderNameAndPriceTimestampGreaterThanEqual(any(), anyString(), any()))
                .thenAnswer(invocation -> prices.stream()
                        .filter(price -> price.getInstrument() == invocation.getArgument(0)
                                && price.getProviderName().equals(invocation.getArgument(1))
                                && !price.getPriceTimestamp().isBefore(invocation.getArgument(2)))
                        .toList()
                );
    }

    private Market market(String name) {
        String zone = "NSE".equalsIgnoreCase(name) ? "Asia/Kolkata" : "Europe/London";
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(zone));
        LocalTime open = now.toLocalTime().plusHours(3).withNano(0);
        return new Market(name, name, zone, open, open.plusMinutes(20));
    }

    private List<Price> pricesFor(String symbol) {
        return prices.stream()
                .filter(price -> symbol.equalsIgnoreCase(price.getInstrument().getSymbol()))
                .toList();
    }

    private Price latest(String symbol) {
        return latestOptional(symbol).orElseThrow();
    }

    private Optional<Price> latestOptional(String symbol) {
        return prices.stream()
                .filter(price -> symbol.equalsIgnoreCase(price.getInstrument().getSymbol()))
                .max((left, right) -> left.getPriceTimestamp().compareTo(right.getPriceTimestamp()));
    }
}
