package com.leaping.portfolio_app.admin.service;

import com.leaping.portfolio_app.admin.dto.AdminClientPageResponse;
import com.leaping.portfolio_app.admin.dto.AdminHoldingPageResponse;
import com.leaping.portfolio_app.admin.dto.AuditEventResponse;
import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.CustomerRepository;
import com.leaping.portfolio_app.audit.entity.AuditEvent;
import com.leaping.portfolio_app.audit.repository.AuditEventRepository;
import com.leaping.portfolio_app.holdings.HoldingResponse;
import com.leaping.portfolio_app.holdings.HoldingService;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    private AdminClientService adminClientService;
    private Customer auditCustomer;

    @BeforeEach
    void setUp() {
        adminClientService = new AdminClientService(
                customerRepository,
                portfolioRepository,
                holdingService,
                auditEventRepository
        );

        auditCustomer = new Customer();
        auditCustomer.setUserId(7L);
        User user = new User();
        user.setId(7L);
        auditCustomer.setUser(user);
    }

    // Tests from dev: paginated client search and holdings.
    @Test
    void getClientsUsesRequestedPageAndReturnsMetadata() {
        Customer customer = new Customer();
        customer.setUserId(101L);

        User user = new User();
        user.setFirstName("Ava");
        user.setLastName("Stone");
        user.setEmail("ava@example.com");
        user.setStatus("ACTIVE");
        customer.setUser(user);

        Page<Customer> page = new PageImpl<>(
                List.of(customer),
                PageRequest.of(1, 100),
                201
        );

        when(customerRepository.findPage(
                eq("ava"),
                eq("active"),
                any(Pageable.class)
        )).thenReturn(page);

        AdminClientPageResponse response = adminClientService.getClients(
                " Ava ",
                " ACTIVE ",
                1,
                100
        );

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findPage(
                eq("ava"),
                eq("active"),
                pageable.capture()
        );

        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(1, response.getPage());
        assertEquals(100, response.getSize());
        assertEquals(201, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertEquals("Ava", response.getClients().get(0).getFirstName());
    }

    @Test
    void getClientsClampsInvalidPageSizeAndPageNumber() {
        Page<Customer> page = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 100),
                0
        );

        when(customerRepository.findPage(
                eq(""),
                eq(""),
                any(Pageable.class)
        )).thenReturn(page);

        adminClientService.getClients(null, null, -1, 1000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findPage(
                eq(""),
                eq(""),
                pageable.capture()
        );

        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
    }

    @Test
    void getClientHoldingsUsesDatabasePageAndReturnsMetadata() {
        Customer customer = new Customer();
        customer.setUserId(101L);

        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(501L);

        when(customerRepository.findById(101L))
                .thenReturn(Optional.of(customer));
        when(portfolioRepository.findByCustomer_UserId(101L))
                .thenReturn(Optional.of(portfolio));
        when(holdingService.getHoldingsByPortfolio(
                eq(501L),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.<HoldingResponse>of(),
                PageRequest.of(1, 100),
                125
        ));

        AdminHoldingPageResponse response = adminClientService.getClientHoldings(
                101L,
                1,
                500
        );

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(holdingService).getHoldingsByPortfolio(
                eq(501L),
                pageable.capture()
        );

        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(1, response.getPage());
        assertEquals(100, response.getSize());
        assertEquals(125, response.getTotalElements());
        assertEquals(2, response.getTotalPages());
    }

    // Tests from feature/audit: client audit history.
    @Test
    void getClientActivityMapsCashAndOrderLinksAndCapsPageSize() {
        AuditEvent event = mock(AuditEvent.class);
        when(event.getAuditEventId()).thenReturn(22L);
        when(event.getEventType()).thenReturn("CASH_BALANCE_CHANGED");
        when(event.getOccurredAt()).thenReturn(
                OffsetDateTime.parse("2026-10-14T10:00:00Z")
        );
        when(event.getUser()).thenReturn(auditCustomer.getUser());
        when(event.getEventDetails()).thenReturn(
                Map.of("amountUsd", new BigDecimal("-25.00"))
        );

        com.leaping.portfolio_app.portfolio.entity.CashTransaction cash =
                new com.leaping.portfolio_app.portfolio.entity.CashTransaction();
        cash.setCashTransactionId(91L);
        when(event.getCashTransaction()).thenReturn(cash);

        when(customerRepository.findById(7L))
                .thenReturn(Optional.of(auditCustomer));
        when(auditEventRepository.findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(
                eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<AuditEventResponse> result = adminClientService.getClientActivity(7L, 2, 1000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(
                eq(7L), pageableCaptor.capture()
        );
        assertEquals(2, pageableCaptor.getValue().getPageNumber());
        assertEquals(100, pageableCaptor.getValue().getPageSize());

        AuditEventResponse response = result.getContent().get(0);
        assertEquals(22L, response.getAuditEventId());
        assertEquals("CASH_BALANCE_CHANGED", response.getEventType());
        assertEquals(7L, response.getUserId());
        assertEquals(91L, response.getCashTransactionId());
        assertEquals(new BigDecimal("-25.00"), response.getEventDetails().get("amountUsd"));
        assertNull(response.getOrderId());
    }

    @Test
    void getClientActivityReturnsEmptyPage() {
        when(customerRepository.findById(7L))
                .thenReturn(Optional.of(auditCustomer));
        when(auditEventRepository.findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(
                eq(7L), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<AuditEventResponse> result = adminClientService.getClientActivity(7L, 0, 25);

        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getContent().size());
    }

    @Test
    void getClientActivityRejectsMissingClient() {
        when(customerRepository.findById(404L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminClientService.getClientActivity(404L, 0, 25)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(auditEventRepository, never())
                .findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(any(), any());
    }
}
