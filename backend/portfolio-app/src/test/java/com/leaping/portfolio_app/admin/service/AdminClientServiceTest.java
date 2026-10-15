package com.leaping.portfolio_app.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.leaping.portfolio_app.admin.dto.AdminClientPageResponse;
import com.leaping.portfolio_app.admin.dto.AdminHoldingPageResponse;
import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.CustomerRepository;
import com.leaping.portfolio_app.holdings.HoldingService;
import com.leaping.portfolio_app.holdings.HoldingResponse;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;

@ExtendWith(MockitoExtension.class)
class AdminClientServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private HoldingService holdingService;

    @InjectMocks
    private AdminClientService adminClientService;

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
                org.springframework.data.domain.PageRequest.of(1, 100),
                201
        );

        when(customerRepository.findPage(
                eq("ava"),
                eq("active"),
                any(Pageable.class)
        )).thenReturn(page);

        AdminClientPageResponse response =
                adminClientService.getClients(
                        " Ava ",
                        " ACTIVE ",
                        1,
                        100
                );

        ArgumentCaptor<Pageable> pageable =
                ArgumentCaptor.forClass(Pageable.class);
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
                org.springframework.data.domain.PageRequest.of(0, 100),
                0
        );

        when(customerRepository.findPage(
                eq(""),
                eq(""),
                any(Pageable.class)
        )).thenReturn(page);

        adminClientService.getClients(null, null, -1, 1000);

        ArgumentCaptor<Pageable> pageable =
                ArgumentCaptor.forClass(Pageable.class);
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
                org.springframework.data.domain.PageRequest.of(1, 100),
                125
        ));

        AdminHoldingPageResponse response =
                adminClientService.getClientHoldings(
                        101L,
                        1,
                        500
                );

        ArgumentCaptor<Pageable> pageable =
                ArgumentCaptor.forClass(Pageable.class);
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
}