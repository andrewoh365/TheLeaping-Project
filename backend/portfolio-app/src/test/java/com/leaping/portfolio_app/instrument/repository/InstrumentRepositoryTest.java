package com.leaping.portfolio_app.instrument.repository;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Market;

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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
class InstrumentRepositoryTest {

    @Container
    static final PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:16")
            .withDatabaseName("instrument_test_db")
            .withUsername("test_user")
            .withPassword("test_password");

    /**
     * Direct both Spring Data and Flyway to the same
     * temporary PostgreSQL test database.
     */
    @DynamicPropertySource
    static void configureDatabase(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.datasource.url",
            postgres::getJdbcUrl
        );

        registry.add(
            "spring.datasource.username",
            postgres::getUsername
        );

        registry.add(
            "spring.datasource.password",
            postgres::getPassword
        );

        registry.add(
            "spring.flyway.url",
            postgres::getJdbcUrl
        );

        registry.add(
            "spring.flyway.user",
            postgres::getUsername
        );

        registry.add(
            "spring.flyway.password",
            postgres::getPassword
        );
    }

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Market market;
    private Currency currency;

    private String suffix;

    @BeforeEach
    void setUp() {

        suffix =
            UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();

        String marketName =
            "NASDAQ-TEST-" + suffix;

        Long marketId =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO markets (
                    market_name,
                    country,
                    timezone,
                    is_active
                )
                VALUES (?, ?, ?, true)
                RETURNING market_id
                """,
                Long.class,
                marketName,
                "United States",
                "America/New_York"
            );

        String currencyCode = "USD";

        jdbcTemplate.update(
            """
            INSERT INTO currencies (
                currency_code,
                currency_name
            )
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """,
            currencyCode,
            "US Dollar"
        );

        market =
            entityManager
                .createQuery(
                    """
                    SELECT m
                    FROM Market m
                    WHERE m.marketId = :id
                    """,
                    Market.class
                )
                .setParameter(
                    "id",
                    marketId
                )
                .getSingleResult();

        currency =
            entityManager
                .createQuery(
                    """
                    SELECT c
                    FROM Currency c
                    WHERE c.currencyCode = :code
                    """,
                    Currency.class
                )
                .setParameter(
                    "code",
                    currencyCode
                )
                .getSingleResult();
    }

    /**
     * Test: searchInstruments returns results
     * matching symbol.
     */
    @Test
    void searchInstruments_withSymbolMatch_shouldReturnInstrument() {

        String symbol =
            "AAPL" + suffix;

        String name =
            "Apple Test " + suffix;

        Instrument apple =
            new Instrument(
                market,
                symbol,
                name,
                InstrumentType.STOCK,
                currency
            );

        instrumentRepository.save(apple);
        entityManager.flush();

        List<Instrument> results =
            instrumentRepository
                .searchInstruments(symbol);

        assertNotNull(results);
        assertEquals(1, results.size());

        assertEquals(
            symbol,
            results.get(0).getSymbol()
        );

        assertEquals(
            name,
            results.get(0).getName()
        );
    }

    /**
     * Test: searchInstruments returns results
     * matching name.
     */
    @Test
    void searchInstruments_withNameMatch_shouldReturnInstrument() {

        String symbol =
            "MSFT" + suffix;

        String name =
            "Microsoft Test " + suffix;

        Instrument microsoft =
            new Instrument(
                market,
                symbol,
                name,
                InstrumentType.STOCK,
                currency
            );

        instrumentRepository.save(microsoft);
        entityManager.flush();

        List<Instrument> results =
            instrumentRepository
                .searchInstruments(name);

        assertNotNull(results);
        assertEquals(1, results.size());

        assertEquals(
            symbol,
            results.get(0).getSymbol()
        );
    }

    /**
     * Test: searchInstruments is
     * case-insensitive for symbol.
     */
    @Test
    void searchInstruments_withLowercaseSymbol_shouldReturnInstrument() {

        String symbol =
            "GOOGL" + suffix;

        Instrument google =
            new Instrument(
                market,
                symbol,
                "Alphabet Test " + suffix,
                InstrumentType.STOCK,
                currency
            );

        instrumentRepository.save(google);
        entityManager.flush();

        List<Instrument> results =
            instrumentRepository
                .searchInstruments(
                    symbol.toLowerCase()
                );

        assertEquals(1, results.size());

        assertEquals(
            symbol,
            results.get(0).getSymbol()
        );
    }

    /**
     * Test: searchInstruments is
     * case-insensitive for name.
     */
    @Test
    void searchInstruments_withLowercaseName_shouldReturnInstrument() {

        String symbol =
            "TSLA" + suffix;

        String name =
            "Tesla Test " + suffix;

        Instrument tesla =
            new Instrument(
                market,
                symbol,
                name,
                InstrumentType.STOCK,
                currency
            );

        instrumentRepository.save(tesla);
        entityManager.flush();

        List<Instrument> results =
            instrumentRepository
                .searchInstruments(
                    name.toLowerCase()
                );

        assertEquals(1, results.size());

        assertEquals(
            symbol,
            results.get(0).getSymbol()
        );
    }

    /**
     * Test: searchInstruments returns
     * partial matches.
     */
    @Test
    void searchInstruments_withPartialMatch_shouldReturnMatches() {

        String commonSearch =
            "APP" + suffix;

        String firstSymbol =
            commonSearch + "A";

        String secondSymbol =
            commonSearch + "B";

        Instrument apple =
            new Instrument(
                market,
                firstSymbol,
                "Apple Test One " + suffix,
                InstrumentType.STOCK,
                currency
            );

        Instrument appleMaps =
            new Instrument(
                market,
                secondSymbol,
                "Apple Test Two " + suffix,
                InstrumentType.STOCK,
                currency
            );

        instrumentRepository.saveAll(
            List.of(
                apple,
                appleMaps
            )
        );

        entityManager.flush();

        List<Instrument> results =
            instrumentRepository
                .searchInstruments(commonSearch);

        assertEquals(2, results.size());

        assertTrue(
            results.stream()
                .anyMatch(
                    instrument ->
                        firstSymbol.equals(
                            instrument.getSymbol()
                        )
                )
        );

        assertTrue(
            results.stream()
                .anyMatch(
                    instrument ->
                        secondSymbol.equals(
                            instrument.getSymbol()
                        )
                )
        );
    }

    /**
     * Test: searchInstruments returns
     * multiple matches with a common name.
     */
    @Test
    void searchInstruments_withCommonNamePart_shouldReturnAllMatches() {

        String commonName =
            "RepositoryTestCompany-" + suffix;

        String firstSymbol =
            "ONE" + suffix;

        String secondSymbol =
            "TWO" + suffix;

        Instrument first =
            new Instrument(
                market,
                firstSymbol,
                commonName + " One",
                InstrumentType.STOCK,
                currency
            );

        Instrument second =
            new Instrument(
                market,
                secondSymbol,
                commonName + " Two",
                InstrumentType.STOCK,
                currency
            );

        instrumentRepository.saveAll(
            List.of(
                first,
                second
            )
        );

        entityManager.flush();

        List<Instrument> results =
            instrumentRepository
                .searchInstruments(commonName);

        assertEquals(2, results.size());

        assertTrue(
            results.stream()
                .anyMatch(
                    instrument ->
                        firstSymbol.equals(
                            instrument.getSymbol()
                        )
                )
        );

        assertTrue(
            results.stream()
                .anyMatch(
                    instrument ->
                        secondSymbol.equals(
                            instrument.getSymbol()
                        )
                )
        );
    }

    /**
     * Test: searchInstruments returns
     * an empty list when there are no matches.
     */
    @Test
    void searchInstruments_withNoMatches_shouldReturnEmptyList() {

        String symbol =
            "AAPL" + suffix;

        Instrument apple =
            new Instrument(
                market,
                symbol,
                "Apple Test " + suffix,
                InstrumentType.STOCK,
                currency
            );

        instrumentRepository.save(apple);
        entityManager.flush();

        List<Instrument> results =
            instrumentRepository
                .searchInstruments(
                    "NO-MATCH-" + suffix
                );

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    /**
     * Test: searchInstruments with
     * an empty string returns an empty list.
     */
    @Test
    void searchInstruments_withEmptyString_shouldReturnEmptyList() {

        String symbol =
            "AAPL" + suffix;

        Instrument apple =
            new Instrument(
                market,
                symbol,
                "Apple Test " + suffix,
                InstrumentType.STOCK,
                currency
            );

        instrumentRepository.save(apple);
        entityManager.flush();

        List<Instrument> results =
            instrumentRepository
                .searchInstruments("");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    /**
     * Test: findByInstrumentId returns
     * the correct instrument.
     */
    @Test
    void findByInstrumentId_withValidId_shouldReturnInstrument() {

        String symbol =
            "AAPL" + suffix;

        Instrument apple =
            new Instrument(
                market,
                symbol,
                "Apple Test " + suffix,
                InstrumentType.STOCK,
                currency
            );

        Instrument saved =
            instrumentRepository.save(apple);

        entityManager.flush();

        Optional<Instrument> result =
            instrumentRepository
                .findByInstrumentId(
                    saved.getInstrumentId()
                );

        assertTrue(result.isPresent());

        assertEquals(
            symbol,
            result.get().getSymbol()
        );
    }

    /**
     * Test: findByInstrumentId returns
     * an empty Optional for an invalid ID.
     */
    @Test
    void findByInstrumentId_withInvalidId_shouldReturnEmpty() {

        Optional<Instrument> result =
            instrumentRepository
                .findByInstrumentId(
                    Long.MAX_VALUE
                );

        assertTrue(result.isEmpty());
    }

    /**
     * Test: findBySymbolIgnoreCase returns
     * the correct instrument.
     */
    @Test
    void findBySymbolIgnoreCase_withValidSymbol_shouldReturnInstrument() {

        String symbol =
            "MSFT" + suffix;

        Instrument microsoft =
            new Instrument(
                market,
                symbol,
                "Microsoft Test " + suffix,
                InstrumentType.STOCK,
                currency
            );

        instrumentRepository.save(microsoft);
        entityManager.flush();

        Optional<Instrument> result =
            instrumentRepository
                .findBySymbolIgnoreCase(
                    symbol.toLowerCase()
                );

        assertTrue(result.isPresent());

        assertEquals(
            symbol,
            result.get().getSymbol()
        );
    }

    /**
     * Test: findBySymbolIgnoreCaseAndIsActiveTrue
     * returns an active instrument.
     */
    @Test
    void findBySymbolIgnoreCaseAndIsActiveTrue_withActiveInstrument_shouldReturn() {

        String symbol =
            "AAPL" + suffix;

        Instrument active =
            new Instrument(
                market,
                symbol,
                "Apple Active Test " + suffix,
                InstrumentType.STOCK,
                currency
            );

        active.setIsActive(true);

        instrumentRepository.save(active);
        entityManager.flush();

        Optional<Instrument> result =
            instrumentRepository
                .findBySymbolIgnoreCaseAndIsActiveTrue(
                    symbol.toLowerCase()
                );

        assertTrue(result.isPresent());

        assertTrue(
            result.get().getIsActive()
        );

        assertEquals(
            symbol,
            result.get().getSymbol()
        );
    }

    /**
     * Test: findByNameContainingIgnoreCase
     * returns partial name matches.
     */
    @Test
    void findByNameContainingIgnoreCase_withPartialName_shouldReturnMatches() {

        String commonName =
            "UniqueApple-" + suffix;

        Instrument first =
            new Instrument(
                market,
                "AAA" + suffix,
                commonName + " One",
                InstrumentType.STOCK,
                currency
            );

        Instrument second =
            new Instrument(
                market,
                "BBB" + suffix,
                commonName + " Two",
                InstrumentType.STOCK,
                currency
            );

        instrumentRepository.saveAll(
            List.of(
                first,
                second
            )
        );

        entityManager.flush();

        List<Instrument> results =
            instrumentRepository
                .findByNameContainingIgnoreCase(
                    commonName.toLowerCase()
                );

        assertEquals(2, results.size());
    }

    /**
     * Test: findByIsActiveTrueAndIsTradeableTrue
     * returns only active and tradeable instruments.
     */
    @Test
    void findByIsActiveTrueAndIsTradeableTrue_shouldReturnOnlyActiveAndTradeable() {

        String activeSymbol =
            "ACTIVE" + suffix;

        String inactiveSymbol =
            "INACTIVE" + suffix;

        Instrument active =
            new Instrument(
                market,
                activeSymbol,
                "Active Instrument " + suffix,
                InstrumentType.STOCK,
                currency
            );

        active.setIsActive(true);
        active.setIsTradeable(true);

        Instrument inactive =
            new Instrument(
                market,
                inactiveSymbol,
                "Inactive Instrument " + suffix,
                InstrumentType.STOCK,
                currency
            );

        inactive.setIsActive(false);
        inactive.setIsTradeable(true);

        instrumentRepository.saveAll(
            List.of(
                active,
                inactive
            )
        );

        entityManager.flush();

        List<Instrument> results =
            instrumentRepository
                .findByIsActiveTrueAndIsTradeableTrue();

        assertNotNull(results);

        /*
         * Flyway may seed other active/tradeable instruments,
         * so we do NOT assume the entire result list has size 1.
         *
         * Instead we verify:
         *   1. every returned row satisfies the repository filter;
         *   2. our active instrument is included;
         *   3. our inactive instrument is excluded.
         */
        assertTrue(
            results.stream()
                .allMatch(
                    instrument ->
                        Boolean.TRUE.equals(
                            instrument.getIsActive()
                        )
                        &&
                        Boolean.TRUE.equals(
                            instrument.getIsTradeable()
                        )
                )
        );

        assertTrue(
            results.stream()
                .anyMatch(
                    instrument ->
                        active.getInstrumentId()
                            .equals(
                                instrument.getInstrumentId()
                            )
                )
        );

        assertFalse(
            results.stream()
                .anyMatch(
                    instrument ->
                        inactive.getInstrumentId()
                            .equals(
                                instrument.getInstrumentId()
                            )
                )
        );
    }
}