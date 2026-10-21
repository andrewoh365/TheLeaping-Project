package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.market.repository.CurrencyRepository;
import com.leaping.portfolio_app.market.repository.MarketRepository;
import com.leaping.portfolio_app.market.repository.PriceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Startup backfill and the first scheduled tick used to invert lock order:
 * the backfill held database row locks, then waited on liveTickLock, while
 * the scheduler held liveTickLock and waited for those rows. This context
 * starts the scheduler during that backfill and must still become ready.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ingestionlock;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                "spring.datasource.driverClassName=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false",
                "market.simulation.enabled=true",
                "market.simulation.respect-market-hours=false",
                "market.simulation.tick-ms=100",
                "market.pricing.refresh-ms=100",
                "market.pricing.history-days=30",
                "jwt.secret=ThisIsATestOnlyJwtSecretKeyWithAtLeast64CharactersForHS512SigningAlgo"
        }
)
@Import(MarketIngestionLockOrderTest.ReferenceDataSeeder.class)
class MarketIngestionLockOrderTest {

    @Autowired
    private PriceRepository priceRepository;

    @Test
    @Timeout(180)
    void startupBackfillCompletesWhileTheSchedulerIsTicking() throws InterruptedException {
        // Startup backfill and the first scheduler tick are asynchronous; poll briefly
        // so the assertion checks readiness rather than immediate startup timing.
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            if (priceRepository.count() > 0) {
                return;
            }
            Thread.sleep(50);
        }
        assertTrue(priceRepository.count() > 0);
    }

    @TestConfiguration
    static class ReferenceDataSeeder {

        @Bean
        ApplicationListener<ApplicationStartedEvent> seedReferenceData(
                MarketRepository markets,
                CurrencyRepository currencies
        ) {
            return event -> {
                saveCurrency(currencies, "USD", "US Dollar", "$", BigDecimal.ONE);
                saveCurrency(currencies, "GBP", "British Pound", "GBP", new BigDecimal("1.35"));
                saveCurrency(currencies, "INR", "Indian Rupee", "INR", new BigDecimal("0.012"));
                saveCurrency(currencies, "EUR", "Euro", "EUR", new BigDecimal("1.17"));
                saveMarket(markets, "NASDAQ", "United States", "America/New_York", LocalTime.of(9, 30), LocalTime.of(16, 0));
                saveMarket(markets, "NYSE", "United States", "America/New_York", LocalTime.of(9, 30), LocalTime.of(16, 0));
                saveMarket(markets, "NSE", "India", "Asia/Kolkata", LocalTime.of(9, 15), LocalTime.of(15, 30));
                saveMarket(markets, "LSE", "United Kingdom", "Europe/London", LocalTime.of(8, 0), LocalTime.of(16, 30));
                saveMarket(markets, "CRYPTO", "Global", "UTC", null, null);
                saveMarket(markets, "FOREX", "Global", "UTC", null, null);
            };
        }

        private static void saveCurrency(
                CurrencyRepository currencies,
                String code,
                String name,
                String symbol,
                BigDecimal rateToUsd
        ) {
            Currency currency = new Currency();
            currency.setCurrencyCode(code);
            currency.setCurrencyName(name);
            currency.setCurrencySymbol(symbol);
            currency.setCurrentExchangeRateToUsd(rateToUsd);
            currency.setExchangeRateUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            currencies.save(currency);
        }

        private static void saveMarket(
                MarketRepository markets,
                String name,
                String country,
                String timezone,
                LocalTime open,
                LocalTime close
        ) {
            Market market = new Market(name, country, timezone, open, close);
            markets.save(market);
        }
    }
}
