package com.leaping.portfolio_app.trade.service;

import com.leaping.portfolio_app.audit.repository.AuditEventRepository;
import com.leaping.portfolio_app.holdings.HoldingRepository;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class TradeServiceAtomicityIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16")
                    .withDatabaseName("trade_atomicity_test_db")
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
    private TradeService tradeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TradeOrderRepository tradeOrderRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @MockitoBean
    private HoldingRepository holdingRepository;

    @Test
    void settlementFailureRollsBackBusinessRowsAndSuccessfulAuditRows() {
        Long orderId = createSubmittedBuyOrder();

        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(
                any(Long.class),
                any(Long.class)
        )).thenReturn(java.util.Optional.empty());
        when(holdingRepository.save(any()))
                .thenThrow(new RuntimeException("holding persistence failed"));

        assertThrows(
                RuntimeException.class,
                () -> tradeService.executeTrade(orderId)
        );

        TradeOrder order = tradeOrderRepository.findById(orderId).orElseThrow();
        assertEquals("ACCEPTED", order.getOrderStatus().name());
        assertEquals(0L, count("execution_attempts", orderId));
        assertEquals(0L, count("trades", orderId));
        assertEquals(0L, count("audit_events", orderId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cash_transactions WHERE portfolio_id = "
                        + "(SELECT portfolio_id FROM trade_orders WHERE order_id = ?)",
                Long.class,
                orderId
        ));
    }

    private Long createSubmittedBuyOrder() {
        String uniqueId = UUID.randomUUID().toString();
        Long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (
                    first_name,
                    last_name,
                    email,
                    user_type,
                    status
                )
                VALUES ('Atomic', 'Tester', ?, 'CUSTOMER', 'ACTIVE')
                RETURNING user_id
                """,
                Long.class,
                "atomic-" + uniqueId + "@example.com"
        );

        jdbcTemplate.update(
                """
                INSERT INTO customers (user_id, date_of_birth, tax_id)
                VALUES (?, DATE '1990-01-01', ?)
                """,
                userId,
                "ATOMIC-" + uniqueId
        );

        Long portfolioId = jdbcTemplate.queryForObject(
                """
                INSERT INTO portfolios (customer_id, cash_balance_usd)
                VALUES (?, 1000000.00)
                RETURNING portfolio_id
                """,
                Long.class,
                userId
        );

        Long instrumentId = jdbcTemplate.queryForObject(
                """
                SELECT i.instrument_id
                FROM instruments i
                JOIN currencies c
                    ON c.currency_code = i.price_currency_code
                WHERE c.currency_code = 'USD'
                  AND EXISTS (
                      SELECT 1
                      FROM prices p
                      WHERE p.instrument_id = i.instrument_id
                  )
                ORDER BY i.instrument_id
                LIMIT 1
                """,
                Long.class
        );

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO trade_orders (
                    portfolio_id,
                    instrument_id,
                    order_action,
                    order_type,
                    order_status,
                    time_in_force,
                    quantity,
                    submitted_at,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, 'BUY', 'MARKET', 'ACCEPTED', 'DAY', 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING order_id
                """,
                Long.class,
                portfolioId,
                instrumentId
        );
    }

    private Long count(String table, Long orderId) {
        String column = table.equals("trades")
                ? "order_id"
                : table.equals("execution_attempts")
                ? "order_id"
                : "order_id";
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class,
                orderId
        );
    }
}