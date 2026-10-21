package com.leaping.portfolio_app.audit.repository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.leaping.portfolio_app.audit.entity.AuditEvent;
import com.leaping.portfolio_app.audit.enums.AuditEventType;
import com.leaping.portfolio_app.audit.service.AuditService;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.trade.entity.TradeOrder;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Testcontainers
@Transactional
class AuditEventRepositoryTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16")
                    .withDatabaseName("audit_test_db")
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
    private AuditEventRepository auditEventRepository;

        @Autowired
        private AuditService auditService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void saveAndQueryByUserAndOrderPersistsJsonTimestampAndStablePaging() {
        String uniqueId = UUID.randomUUID().toString();
        Long userId = createUser(uniqueId);
        Long orderId = createOrder();

        User user = entityManager.getReference(User.class, userId);
        TradeOrder order = entityManager.getReference(TradeOrder.class, orderId);

        AuditEvent saved = auditService.recordEvent(
                AuditEventType.ORDER_ACCEPTED,
                user,
                order,
                null,
                null,
                null,
                Map.of("orderId", orderId, "source", "unit-test")
        );

        entityManager.flush();

        assertNotNull(saved.getAuditEventId());
        assertNotNull(saved.getOccurredAt());

        OffsetDateTime tieTimestamp = saved.getOccurredAt().plusSeconds(1);
        Long firstTiedId = insertAuditEvent(
                userId,
                orderId,
                tieTimestamp,
                "PRICE_CAPTURED"
        );
        Long secondTiedId = insertAuditEvent(
                userId,
                orderId,
                tieTimestamp,
                "TRADE_FILLED"
        );

        entityManager.clear();

        Page<AuditEvent> firstPage =
                auditEventRepository
                        .findByUser_IdOrderByOccurredAtAscAuditEventIdAsc(
                                userId,
                                PageRequest.of(0, 2)
                        );
        Page<AuditEvent> orderPage =
                auditEventRepository
                        .findByOrder_OrderIdOrderByOccurredAtAscAuditEventIdAsc(
                                orderId,
                                PageRequest.of(0, 10)
                        );

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getNumberOfElements());
        assertEquals(saved.getAuditEventId(), firstPage.getContent().get(0).getAuditEventId());
        assertEquals(firstTiedId, firstPage.getContent().get(1).getAuditEventId());
        assertEquals(3, orderPage.getTotalElements());
        assertEquals(firstTiedId, orderPage.getContent().get(1).getAuditEventId());
        assertEquals(secondTiedId, orderPage.getContent().get(2).getAuditEventId());
        Map<String, Object> persistedDetails =
                orderPage.getContent().get(0).getEventDetails();
        assertEquals("unit-test", persistedDetails.get("source"));
        assertEquals(
                orderId,
                ((Number) persistedDetails.get("orderId")).longValue()
        );
    }

    @Test
    void updateAuditEventIsBlockedByAppendOnlyTrigger() {
        String uniqueId = UUID.randomUUID().toString();
        Long userId = createUser(uniqueId);
        Long orderId = createOrder();
        Long auditEventId = insertAuditEvent(
                userId,
                orderId,
                OffsetDateTime.now(),
                "ORDER_SUBMITTED"
        );

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        "UPDATE audit_events SET event_type = ? WHERE audit_event_id = ?",
                        "TAMPERED",
                        auditEventId
                )
        );
    }

    @Test
    void deleteAuditEventIsBlockedByAppendOnlyTrigger() {
        String uniqueId = UUID.randomUUID().toString();
        Long userId = createUser(uniqueId);
        Long orderId = createOrder();
        Long auditEventId = insertAuditEvent(
                userId,
                orderId,
                OffsetDateTime.now(),
                "ORDER_SUBMITTED"
        );

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        "DELETE FROM audit_events WHERE audit_event_id = ?",
                        auditEventId
                )
        );
    }

    @Test
    void seededAuditEventsRemainAvailableWithoutFabricatingSettlementEvents() {
        Long seededEventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_events
                WHERE event_details ->> 'seed' = 'true'
                """,
                Long.class
        );

        assertNotNull(seededEventCount);
        org.junit.jupiter.api.Assertions.assertTrue(seededEventCount > 0);

        Long seededOrderId = jdbcTemplate.queryForObject(
                """
                SELECT order_id
                FROM audit_events
                WHERE event_details ->> 'seed' = 'true'
                  AND order_id IS NOT NULL
                ORDER BY audit_event_id
                LIMIT 1
                """,
                Long.class
        );

        Page<AuditEvent> events = auditEventRepository
                .findByOrder_OrderIdOrderByOccurredAtAscAuditEventIdAsc(
                        seededOrderId,
                        PageRequest.of(0, 100)
                );

        assertEquals(true, events.getContent().stream()
                .allMatch(event -> Boolean.TRUE.equals(
                        event.getEventDetails().get("seed")
                )));
    }

    private Long createUser(String uniqueId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users (
                    first_name,
                    last_name,
                    email,
                    user_type,
                    status
                )
                VALUES (?, ?, ?, 'CUSTOMER', 'ACTIVE')
                RETURNING user_id
                """,
                Long.class,
                "Audit",
                "Tester",
                "audit-" + uniqueId + "@example.com"
        );
    }

    private Long createOrder() {
        Long portfolioId = jdbcTemplate.queryForObject(
                "SELECT portfolio_id FROM portfolios ORDER BY portfolio_id LIMIT 1",
                Long.class
        );
        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT instrument_id FROM instruments ORDER BY instrument_id LIMIT 1",
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
                VALUES (?, ?, 'BUY', 'MARKET', 'SUBMITTED', 'DAY', 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING order_id
                """,
                Long.class,
                portfolioId,
                instrumentId
        );
    }

    private Long insertAuditEvent(
            Long userId,
            Long orderId,
            OffsetDateTime occurredAt,
            String eventType
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO audit_events (
                    user_id,
                    order_id,
                    event_type,
                    event_details,
                    occurred_at
                )
                VALUES (?, ?, ?, '{}'::jsonb, ?)
                RETURNING audit_event_id
                """,
                Long.class,
                userId,
                orderId,
                eventType,
                occurredAt
        );
    }
}