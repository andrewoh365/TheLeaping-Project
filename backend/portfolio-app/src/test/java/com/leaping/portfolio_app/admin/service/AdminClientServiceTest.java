package com.leaping.portfolio_app.admin.service;

import com.leaping.portfolio_app.admin.dto.AuditEventResponse;
import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.CustomerRepository;
import com.leaping.portfolio_app.audit.entity.AuditEvent;
import com.leaping.portfolio_app.audit.repository.AuditEventRepository;
import com.leaping.portfolio_app.holdings.HoldingService;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import com.leaping.portfolio_app.trade.entity.ExecutionAttempt;
import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminClientServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private HoldingService holdingService;

    @Mock
    private AuditEventRepository auditEventRepository;

    private AdminClientService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new AdminClientService(
                customerRepository,
                portfolioRepository,
                holdingService,
                auditEventRepository
        );

        customer = new Customer();
        customer.setUserId(7L);
        User user = new User();
        user.setId(7L);
        customer.setUser(user);
    }

    @Test
    void getClientActivityMapsCashAndOrderLinksAndCapsPageSize() {
        AuditEvent event = mock(AuditEvent.class);
        when(event.getAuditEventId()).thenReturn(22L);
        when(event.getEventType()).thenReturn("CASH_BALANCE_CHANGED");
        when(event.getOccurredAt()).thenReturn(
                OffsetDateTime.parse("2026-10-14T10:00:00Z")
        );
        when(event.getUser()).thenReturn(customer.getUser());
        when(event.getEventDetails()).thenReturn(
                Map.of("amountUsd", new java.math.BigDecimal("-25.00"))
        );
        com.leaping.portfolio_app.portfolio.entity.CashTransaction cash =
                new com.leaping.portfolio_app.portfolio.entity.CashTransaction();
        cash.setCashTransactionId(91L);
        when(event.getCashTransaction()).thenReturn(cash);

        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(auditEventRepository.findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(
                eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<AuditEventResponse> result = service.getClientActivity(7L, 2, 1000);

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(
                eq(7L), pageableCaptor.capture());
        assertEquals(2, pageableCaptor.getValue().getPageNumber());
        assertEquals(100, pageableCaptor.getValue().getPageSize());

        AuditEventResponse response = result.getContent().get(0);
        assertEquals(22L, response.getAuditEventId());
        assertEquals("CASH_BALANCE_CHANGED", response.getEventType());
        assertEquals(7L, response.getUserId());
        assertEquals(91L, response.getCashTransactionId());
        assertEquals(new java.math.BigDecimal("-25.00"), response.getEventDetails().get("amountUsd"));
        org.junit.jupiter.api.Assertions.assertNull(response.getOrderId());
    }

    @Test
    void getClientActivityReturnsEmptyPage() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(auditEventRepository.findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(
                eq(7L), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<AuditEventResponse> result = service.getClientActivity(7L, 0, 25);

        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getContent().size());
    }

    @Test
    void getClientActivityRejectsMissingClient() {
        when(customerRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getClientActivity(404L, 0, 25)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(auditEventRepository, never())
                .findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(any(), any());
    }
}