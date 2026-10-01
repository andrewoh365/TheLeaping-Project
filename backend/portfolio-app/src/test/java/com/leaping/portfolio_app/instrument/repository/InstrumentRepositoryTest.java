package com.leaping.portfolio_app.instrument.repository;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.repository.InstrumentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
class InstrumentRepositoryTest {

    @Container
    static final PostgreSQLContainer postgres
            = new PostgreSQLContainer("postgres:16")
                    .withDatabaseName("instrument_test_db")
                    .withUsername("test_user")
                    .withPassword("test_password");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Market market;
    private Currency currency;

    @BeforeEach
    void setUp() {
        String uniqueId = UUID.randomUUID().toString();
        Long marketId = jdbcTemplate.queryForObject("""
                INSERT INTO markets (market_name, country, timezone, is_active)
                VALUES (?, ?, ?, true)
                RETURNING market_id
                """,
                Long.class,
                "NASDAQ-" + uniqueId,
                "United States",
                "America/New_York"
        );

        String currencyCode = "USD";
        jdbcTemplate.update("""
                INSERT INTO currencies (currency_code, currency_name)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """,
                currencyCode, "US Dollar"
        );

        market = entityManager.createQuery(
                "SELECT m FROM Market m WHERE m.marketId = :id",
                Market.class
        ).setParameter("id", marketId).getSingleResult();

        currency = entityManager.createQuery(
                "SELECT c FROM Currency c WHERE c.currencyCode = :code",
                Currency.class
        ).setParameter("code", currencyCode).getSingleResult();
    }

    /**
     * Test: searchInstruments returns results matching symbol
     */
    @Test
    void searchInstruments_withSymbolMatch_shouldReturnInstrument() {
        Instrument apple = new Instrument(market, "AAPL", "Apple Inc.", InstrumentType.STOCK, currency);
        instrumentRepository.save(apple);
        entityManager.flush();

        List<Instrument> results = instrumentRepository.searchInstruments("AAPL");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("AAPL", results.get(0).getSymbol());
        assertEquals("Apple Inc.", results.get(0).getName());
    }

    /**
     * Test: searchInstruments returns results matching name
     */
    @Test
    void searchInstruments_withNameMatch_shouldReturnInstrument() {
        // Arrange: Create test instrument
        Instrument microsoft = new Instrument(market, "MSFT", "Microsoft Corporation", InstrumentType.STOCK, currency);
        instrumentRepository.save(microsoft);
        entityManager.flush();

        List<Instrument> results = instrumentRepository.searchInstruments("Microsoft");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("MSFT", results.get(0).getSymbol());
    }

    /**
     * Test: searchInstruments is case-insensitive for symbol
     */
    @Test
    void searchInstruments_withLowercaseSymbol_shouldReturnInstrument() {
        Instrument google = new Instrument(market, "GOOGL", "Alphabet Inc.", InstrumentType.STOCK, currency);
        instrumentRepository.save(google);
        entityManager.flush();

        List<Instrument> results = instrumentRepository.searchInstruments("googl");

        assertEquals(1, results.size());
        assertEquals("GOOGL", results.get(0).getSymbol());
    }

    /**
     * Test: searchInstruments is case-insensitive for name
     */
    @Test
    void searchInstruments_withLowercaseName_shouldReturnInstrument() {
        Instrument tesla = new Instrument(market, "TSLA", "Tesla Inc.", InstrumentType.STOCK, currency);
        instrumentRepository.save(tesla);
        entityManager.flush();

        List<Instrument> results = instrumentRepository.searchInstruments("tesla");

        assertEquals(1, results.size());
        assertEquals("TSLA", results.get(0).getSymbol());
    }

    /**
     * Test: searchInstruments returns partial matches
     */
    @Test
    void searchInstruments_withPartialMatch_shouldReturnMatches() {
        // Arrange: Create multiple instruments
        Instrument apple = new Instrument(market, "AAPL", "Apple Inc.", InstrumentType.STOCK, currency);
        Instrument appleMaps = new Instrument(market, "APPL", "Apple Maps Corp.", InstrumentType.STOCK, currency);
        instrumentRepository.saveAll(List.of(apple, appleMaps));
        entityManager.flush();

        List<Instrument> results = instrumentRepository.searchInstruments("APP");

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(i -> "AAPL".equals(i.getSymbol())));
        assertTrue(results.stream().anyMatch(i -> "APPL".equals(i.getSymbol())));
    }

    /**
     * Test: searchInstruments returns multiple matches with common name
     */
    @Test
    void searchInstruments_withCommonNamePart_shouldReturnAllMatches() {
        // Arrange: Create instruments with "Inc." in name
        Instrument apple = new Instrument(market, "AAPL", "Apple Inc.", InstrumentType.STOCK, currency);
        Instrument google = new Instrument(market, "GOOGL", "Alphabet Inc.", InstrumentType.STOCK, currency);
        instrumentRepository.saveAll(List.of(apple, google));
        entityManager.flush();

        List<Instrument> results = instrumentRepository.searchInstruments("Inc");

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(i -> "AAPL".equals(i.getSymbol())));
        assertTrue(results.stream().anyMatch(i -> "GOOGL".equals(i.getSymbol())));
    }

    /**
     * Test: searchInstruments returns empty list for no matches
     */
    @Test
    void searchInstruments_withNoMatches_shouldReturnEmptyList() {
        Instrument apple = new Instrument(market, "AAPL", "Apple Inc.", InstrumentType.STOCK, currency);
        instrumentRepository.save(apple);
        entityManager.flush();

        List<Instrument> results = instrumentRepository.searchInstruments("NONEXISTENT");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    /**
     * Test: searchInstruments with empty string
     */
    @Test
    void searchInstruments_withEmptyString_shouldReturnEmptyList() {
        Instrument apple = new Instrument(market, "AAPL", "Apple Inc.", InstrumentType.STOCK, currency);
        instrumentRepository.save(apple);
        entityManager.flush();

        List<Instrument> results = instrumentRepository.searchInstruments("");

        assertTrue(results.isEmpty());
    }

    /**
     * Test: findByInstrumentId returns correct instrument
     */
    @Test
    void findByInstrumentId_withValidId_shouldReturnInstrument() {
        Instrument apple = new Instrument(market, "AAPL", "Apple Inc.", InstrumentType.STOCK, currency);
        Instrument saved = instrumentRepository.save(apple);
        entityManager.flush();

        Optional<Instrument> result = instrumentRepository.findByInstrumentId(saved.getInstrumentId());

        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().getSymbol());
    }

    /**
     * Test: findByInstrumentId returns empty Optional for invalid ID
     */
    @Test
    void findByInstrumentId_withInvalidId_shouldReturnEmpty() {
        Optional<Instrument> result = instrumentRepository.findByInstrumentId(999L);

        assertTrue(result.isEmpty());
    }

    /**
     * Test: findBySymbolIgnoreCase returns correct instrument
     */
    @Test
    void findBySymbolIgnoreCase_withValidSymbol_shouldReturnInstrument() {
        Instrument microsoft = new Instrument(market, "MSFT", "Microsoft", InstrumentType.STOCK, currency);
        instrumentRepository.save(microsoft);
        entityManager.flush();

        Optional<Instrument> result = instrumentRepository.findBySymbolIgnoreCase("msft");

        assertTrue(result.isPresent());
        assertEquals("MSFT", result.get().getSymbol());
    }

    /**
     * Test: findBySymbolIgnoreCaseAndIsActiveTrue returns active instruments
     */
    @Test
    void findBySymbolIgnoreCaseAndIsActiveTrue_withActiveInstrument_shouldReturn() {
        Instrument active = new Instrument(market, "AAPL", "Apple Inc.", InstrumentType.STOCK, currency);
        active.setIsActive(true);
        instrumentRepository.save(active);
        entityManager.flush();

        Optional<Instrument> result = instrumentRepository.findBySymbolIgnoreCaseAndIsActiveTrue("aapl");

        assertTrue(result.isPresent());
        assertTrue(result.get().getIsActive());
    }

    /**
     * Test: findByNameContainingIgnoreCase returns partial name matches
     */
    @Test
    void findByNameContainingIgnoreCase_withPartialName_shouldReturnMatches() {
        Instrument apple = new Instrument(market, "AAPL", "Apple Inc.", InstrumentType.STOCK, currency);
        Instrument appleMaps = new Instrument(market, "APPL", "Apple Maps", InstrumentType.STOCK, currency);
        instrumentRepository.saveAll(List.of(apple, appleMaps));
        entityManager.flush();

        List<Instrument> results = instrumentRepository.findByNameContainingIgnoreCase("apple");

        assertEquals(2, results.size());
    }

    /**
     * Test: findByIsActiveTrueAndIsTradeableTrue returns only active and tradeable
     */
    @Test
    void findByIsActiveTrueAndIsTradeableTrue_shouldReturnOnlyActiveAndTradeable() {
        Instrument active = new Instrument(market, "AAPL", "Apple Inc.", InstrumentType.STOCK, currency);
        active.setIsActive(true);
        active.setIsTradeable(true);

        Instrument inactive = new Instrument(market, "MSFT", "Microsoft", InstrumentType.STOCK, currency);
        inactive.setIsActive(false);
        inactive.setIsTradeable(true);

        instrumentRepository.saveAll(List.of(active, inactive));
        entityManager.flush();

        List<Instrument> results = instrumentRepository.findByIsActiveTrueAndIsTradeableTrue();

        assertEquals(1, results.size());
        assertEquals("AAPL", results.get(0).getSymbol());
    }
}
