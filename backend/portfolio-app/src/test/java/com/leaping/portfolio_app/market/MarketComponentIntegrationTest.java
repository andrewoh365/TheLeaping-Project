package com.leaping.portfolio_app.market;

import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.market.repository.CurrencyRepository;
import com.leaping.portfolio_app.market.repository.MarketRepository;
import com.leaping.portfolio_app.market.repository.PriceRepository;
import com.leaping.portfolio_app.market.service.MarketDataIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:markettest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                "spring.datasource.driverClassName=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false",
                "spring.profiles.active=test",
                "jwt.secret=ThisIsATestOnlyJwtSecretKeyWithAtLeast64CharactersForHS512SigningAlgo",
                "market.pricing.refresh-ms=31536000000"
        }
)
class MarketComponentIntegrationTest {

        private MockMvc mockMvc;

        @Autowired
        private WebApplicationContext webApplicationContext;

    @Autowired
    private MarketDataIngestionService marketDataIngestionService;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private PriceRepository priceRepository;

    @BeforeEach
    void seedReferenceDataAndIngest() {
                this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                                .apply(springSecurity())
                                .build();
        seedCurrencies();
        seedMarkets();
        marketDataIngestionService.scheduledRefresh();
    }

    @Test
    void csvUniverse_shouldIngestInstrumentsAndPricesIntoDatabase() {
        long instrumentCount = instrumentRepository.count();
        long priceCount = priceRepository.count();

        assertTrue(instrumentCount >= 100, "Expected at least 100 ingested instruments");
        assertTrue(priceCount > 0, "Expected generated price history rows");

        assertTrue(instrumentRepository.findBySymbolIgnoreCase("AAPL").isPresent());
        assertTrue(instrumentRepository.findBySymbolIgnoreCase("BTC-USD").isPresent());
        assertTrue(instrumentRepository.findBySymbolIgnoreCase("EUR/USD").isPresent());

        assertTrue(priceRepository.findFirstByInstrumentSymbolIgnoreCaseOrderByPriceTimestampDesc("AAPL").isPresent());
        assertTrue(priceRepository.findFirstByInstrumentSymbolIgnoreCaseOrderByPriceTimestampDesc("BTC-USD").isPresent());
    }

    @Test
    @WithMockUser(username = "market.tester@example.com", roles = {"CUSTOMER"})
    void marketEndpoints_shouldReturnSuccessfulResponsesWithIngestedData() throws Exception {
        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = to.minusDays(7);

        mockMvc.perform(get("/api/market/exchanges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)));

        mockMvc.perform(get("/api/market/exchanges/NASDAQ/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketName").value("NASDAQ"));

        mockMvc.perform(get("/api/market/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());

        mockMvc.perform(get("/api/market/instruments/price/latest").param("symbol", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.provider").value("csv-universe-mock"));

        mockMvc.perform(get("/api/market/instruments/price/history")
                        .param("symbol", "AAPL")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/market/instruments/quote").param("symbol", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.provider").value("csv-universe-mock"));

        mockMvc.perform(get("/api/market/quotes")
                        .param("symbols", "AAPL", "BTC-USD", "EUR/USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/market/instruments/tradability").param("symbol", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.reason").exists());
    }

    @Test
    @WithMockUser(username = "market.tester@example.com", roles = {"CUSTOMER"})
    void unknownSymbolEndpoints_shouldReturnExpectedContractResponses() throws Exception {
        mockMvc.perform(get("/api/market/instruments/price/latest").param("symbol", "ZZZ_UNKNOWN"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/market/instruments/quote").param("symbol", "ZZZ_UNKNOWN"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/market/instruments/tradability").param("symbol", "ZZZ_UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("INSTRUMENT_NOT_FOUND"));
    }

        @Test
        void marketEndpoints_shouldRequireAuthentication() throws Exception {
                mockMvc.perform(get("/api/market/exchanges"))
                                .andExpect(status().isForbidden());
        }

        @Test
        void repeatedIngestion_shouldNotDuplicateSameProviderTimestampRows() {
                long firstCount = priceRepository.count();
                marketDataIngestionService.scheduledRefresh();
                long secondCount = priceRepository.count();

                assertTrue(firstCount > 0, "Expected initial price rows");
                assertTrue(secondCount >= firstCount, "Price count should not decrease");
                assertTrue(secondCount - firstCount < 100, "Repeated ingestion should not bulk-duplicate same-day rows");
        }

    private void seedCurrencies() {
        upsertCurrency("USD", "US Dollar", "$", BigDecimal.ONE);
        upsertCurrency("GBP", "British Pound", "GBP", new BigDecimal("1.30"));
        upsertCurrency("INR", "Indian Rupee", "INR", new BigDecimal("0.012"));
    }

    private void upsertCurrency(String code, String name, String symbol, BigDecimal rateToUsd) {
        Currency currency = currencyRepository.findById(code).orElseGet(Currency::new);
        currency.setCurrencyCode(code);
        currency.setCurrencyName(name);
        currency.setCurrencySymbol(symbol);
        currency.setCurrentExchangeRateToUsd(rateToUsd);
        currency.setExchangeRateUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        currencyRepository.save(currency);
    }

    private void seedMarkets() {
        upsertMarket("NASDAQ", "United States", "America/New_York", LocalTime.of(9, 30), LocalTime.of(16, 0));
        upsertMarket("NYSE", "United States", "America/New_York", LocalTime.of(9, 30), LocalTime.of(16, 0));
        upsertMarket("NSE", "India", "Asia/Kolkata", LocalTime.of(9, 15), LocalTime.of(15, 30));
        upsertMarket("LSE", "United Kingdom", "Europe/London", LocalTime.of(8, 0), LocalTime.of(16, 30));
        upsertMarket("CRYPTO", "Global", "UTC", null, null);
        upsertMarket("FOREX", "Global", "UTC", null, null);
    }

    private void upsertMarket(String name, String country, String timezone, LocalTime open, LocalTime close) {
        Market market = marketRepository.findByMarketNameIgnoreCase(name).orElseGet(Market::new);
        market.setMarketName(name);
        market.setCountry(country);
        market.setTimezone(timezone);
        market.setOpenTime(open);
        market.setCloseTime(close);
        market.setIsActive(true);
        Market saved = marketRepository.save(market);
        assertNotNull(saved.getMarketId());
    }
}
