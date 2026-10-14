package com.leaping.portfolio_app.admin.service;

import com.leaping.portfolio_app.admin.dto.AuditEventResponse;
import com.leaping.portfolio_app.audit.entity.AuditEvent;
import com.leaping.portfolio_app.audit.repository.AuditEventRepository;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTradeLookupServiceTest {

    @Mock
    private TradeOrderRepository tradeOrderRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Test
    void getOrderAuditReturnsRepositoryOrderAndStructuredDetails() {
        AdminTradeLookupService service = new AdminTradeLookupService(
                tradeOrderRepository,
                tradeRepository,
                auditEventRepository
        );
        TradeOrder order = new TradeOrder();
        order.setOrderId(42L);
        AuditEvent older = event(1L, "ORDER_SUBMITTED", "2026-10-14T09:00:00Z");
        AuditEvent newer = event(2L, "TRADE_FILLED", "2026-10-14T09:00:01Z");

        when(tradeOrderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(auditEventRepository.findByOrder_OrderIdOrderByOccurredAtAscAuditEventIdAsc(
                eq(42L), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(older, newer)));

        List<AuditEventResponse> result = service.getOrderAudit(42L);

        assertEquals(List.of("ORDER_SUBMITTED", "TRADE_FILLED"),
                result.stream().map(AuditEventResponse::getEventType).toList());
        assertEquals(1L, result.get(0).getAuditEventId());
        assertEquals(42L, result.get(0).getOrderId());
        assertEquals("42", result.get(0).getEventDetails().get("orderId"));
    }

    @Test
    void getOrderAuditReturnsEmptyTimelineForExistingOrder() {
        AdminTradeLookupService service = new AdminTradeLookupService(
                tradeOrderRepository,
                tradeRepository,
                auditEventRepository
        );
        TradeOrder order = new TradeOrder();
        order.setOrderId(42L);
        when(tradeOrderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(auditEventRepository.findByOrder_OrderIdOrderByOccurredAtAscAuditEventIdAsc(
                eq(42L), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of()));

        assertEquals(0, service.getOrderAudit(42L).size());
    }

    @Test
    void getOrderAuditRejectsMissingOrder() {
        AdminTradeLookupService service = new AdminTradeLookupService(
                tradeOrderRepository,
                tradeRepository,
                auditEventRepository
        );
        when(tradeOrderRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getOrderAudit(404L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(auditEventRepository, never())
                .findByOrder_OrderIdOrderByOccurredAtAscAuditEventIdAsc(eq(404L), eq(Pageable.unpaged()));
    }

    private AuditEvent event(Long id, String type, String occurredAt) {
        AuditEvent event = mock(AuditEvent.class);
        when(event.getAuditEventId()).thenReturn(id);
        when(event.getEventType()).thenReturn(type);
        when(event.getEventDetails()).thenReturn(Map.of("orderId", "42"));
        TradeOrder order = new TradeOrder();
        order.setOrderId(42L);
        when(event.getOrder()).thenReturn(order);
        when(event.getOccurredAt())
                .thenReturn(OffsetDateTime.parse(occurredAt));
        return event;
    }
}