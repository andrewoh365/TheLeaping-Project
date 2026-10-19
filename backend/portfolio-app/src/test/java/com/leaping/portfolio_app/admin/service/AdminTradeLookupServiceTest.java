
package com.leaping.portfolio_app.admin.service;

import com.leaping.portfolio_app.admin.dto.AdminOrderPageResponse;
import com.leaping.portfolio_app.admin.dto.AuditEventResponse;
import com.leaping.portfolio_app.audit.entity.AuditEvent;
import com.leaping.portfolio_app.audit.repository.AuditEventRepository;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    private AdminTradeLookupService adminTradeLookupService;

    @BeforeEach
    void setUp() {
        adminTradeLookupService = new AdminTradeLookupService(
                tradeOrderRepository,
                tradeRepository,
                auditEventRepository
        );
    }

    // DEV: Preserve database pagination and metadata test.
    @Test
    void getOrdersUsesDatabasePageAndReturnsMetadata() {
        when(tradeOrderRepository.findAdminPage(
                eq("ava"),
                eq("filled"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(2, 100),
                250
        ));

        AdminOrderPageResponse response =
                adminTradeLookupService.getOrders(
                        " Ava ",
                        " FILLED ",
                        2,
                        500
                );

        ArgumentCaptor<Pageable> pageable =
                ArgumentCaptor.forClass(Pageable.class);

        verify(tradeOrderRepository).findAdminPage(
                eq("ava"),
                eq("filled"),
                pageable.capture()
        );

        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(2, response.getPage());
        assertEquals(100, response.getSize());
        assertEquals(250, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
    }

    // AUDIT: Verify chronological order and structured event details.
    @Test
    void getOrderAuditReturnsRepositoryOrderAndStructuredDetails() {
        TradeOrder order = new TradeOrder();
        order.setOrderId(42L);

        AuditEvent older = event(
                1L,
                "ORDER_SUBMITTED",
                "2026-10-14T09:00:00Z"
        );

        AuditEvent newer = event(
                2L,
                "TRADE_FILLED",
                "2026-10-14T09:00:01Z"
        );

        when(tradeOrderRepository.findById(42L))
                .thenReturn(Optional.of(order));

        when(auditEventRepository
                .findByOrder_OrderIdOrderByOccurredAtAscAuditEventIdAsc(
                        eq(42L),
                        eq(Pageable.unpaged())
                ))
                .thenReturn(new PageImpl<>(List.of(older, newer)));

        List<AuditEventResponse> result =
                adminTradeLookupService.getOrderAudit(42L);

        assertEquals(
                List.of("ORDER_SUBMITTED", "TRADE_FILLED"),
                result.stream()
                        .map(AuditEventResponse::getEventType)
                        .toList()
        );

        assertEquals(1L, result.get(0).getAuditEventId());
        assertEquals(42L, result.get(0).getOrderId());
        assertEquals(
                "42",
                result.get(0).getEventDetails().get("orderId")
        );
    }

    // AUDIT: Existing orders may have no audit events.
    @Test
    void getOrderAuditReturnsEmptyTimelineForExistingOrder() {
        TradeOrder order = new TradeOrder();
        order.setOrderId(42L);

        when(tradeOrderRepository.findById(42L))
                .thenReturn(Optional.of(order));

        when(auditEventRepository
                .findByOrder_OrderIdOrderByOccurredAtAscAuditEventIdAsc(
                        eq(42L),
                        eq(Pageable.unpaged())
                ))
                .thenReturn(new PageImpl<>(List.of()));

        assertEquals(
                0,
                adminTradeLookupService.getOrderAudit(42L).size()
        );
    }

    // AUDIT: Missing orders should return 404.
    @Test
    void getOrderAuditRejectsMissingOrder() {
        when(tradeOrderRepository.findById(404L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminTradeLookupService.getOrderAudit(404L)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(auditEventRepository, never())
                .findByOrder_OrderIdOrderByOccurredAtAscAuditEventIdAsc(
                        eq(404L),
                        eq(Pageable.unpaged())
                );
    }

    // Helper used by the Audit tests.
    private AuditEvent event(
            Long id,
            String type,
            String occurredAt
    ) {
        AuditEvent event = mock(AuditEvent.class);

        when(event.getAuditEventId()).thenReturn(id);
        when(event.getEventType()).thenReturn(type);
        when(event.getEventDetails())
                .thenReturn(Map.of("orderId", "42"));

        TradeOrder order = new TradeOrder();
        order.setOrderId(42L);

        when(event.getOrder()).thenReturn(order);
        when(event.getOccurredAt())
                .thenReturn(OffsetDateTime.parse(occurredAt));

        return event;
    }
}
