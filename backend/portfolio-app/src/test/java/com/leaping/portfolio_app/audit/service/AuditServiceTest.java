package com.leaping.portfolio_app.audit.service;

import com.leaping.portfolio_app.audit.entity.AuditEvent;
import com.leaping.portfolio_app.audit.enums.AuditEventType;
import com.leaping.portfolio_app.audit.repository.AuditEventRepository;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.portfolio.entity.CashTransaction;
import com.leaping.portfolio_app.trade.entity.ExecutionAttempt;
import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void recordEventCreatesLinkedEventWithCopiedStructuredDetails() {
        User user = new User();
        TradeOrder order = new TradeOrder();
        ExecutionAttempt executionAttempt = new ExecutionAttempt();
        Trade trade = new Trade();
        CashTransaction cashTransaction = new CashTransaction();
        Map<String, Object> details = Map.of(
                "symbol", "AAPL",
                "quantity", 5,
                "priceUsd", 195.25
        );

        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuditEvent saved = auditService.recordEvent(
                AuditEventType.TRADE_FILLED,
                user,
                order,
                executionAttempt,
                trade,
                cashTransaction,
                details
        );

        ArgumentCaptor<AuditEvent> eventCaptor =
                ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(eventCaptor.capture());

        AuditEvent captured = eventCaptor.getValue();
        assertSame(captured, saved);
        assertEquals("TRADE_FILLED", captured.getEventType());
        assertSame(user, captured.getUser());
        assertSame(order, captured.getOrder());
        assertSame(executionAttempt, captured.getExecutionAttempt());
        assertSame(trade, captured.getTrade());
        assertSame(cashTransaction, captured.getCashTransaction());
        assertEquals(details, captured.getEventDetails());
        assertNotSame(details, captured.getEventDetails());
    }

    @Test
    void recordEventConvertsJavaTimeDetailsToJsonSafeStrings() {
        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuditEvent saved = auditService.recordEvent(
                AuditEventType.CASH_TRANSACTION_RECORDED,
                new User(),
                null,
                null,
                null,
                new CashTransaction(),
                Map.of("createdAt", OffsetDateTime.parse("2026-10-14T10:15:30Z"))
        );

        assertEquals(
                "2026-10-14T10:15:30Z",
                saved.getEventDetails().get("createdAt")
        );
    }

    @Test
    void recordEventPropagatesPersistenceFailure() {
        RuntimeException persistenceFailure = new RuntimeException(
                "database unavailable"
        );
        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenThrow(persistenceFailure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> auditService.recordEvent(
                        AuditEventType.ORDER_ACCEPTED,
                        new User(),
                        null,
                        null,
                        null,
                        null,
                        Map.of("orderId", 42L)
                )
        );

        assertSame(persistenceFailure, thrown);
    }

    @Test
    void recordEventRejectsSensitiveDetailKeysBeforePersistence() {
                IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> auditService.recordEvent(
                        AuditEventType.ORDER_ACCEPTED,
                        new User(),
                        null,
                        null,
                        null,
                        null,
                        Map.of("jwtToken", "should-not-be-recorded")
                )
        );

        assertEquals(
                "sensitive values must not be recorded in audit details",
                thrown.getMessage()
        );
        verify(auditEventRepository, never()).save(any(AuditEvent.class));
    }
}