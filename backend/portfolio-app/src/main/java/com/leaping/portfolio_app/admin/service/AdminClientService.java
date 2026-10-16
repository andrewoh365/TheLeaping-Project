package com.leaping.portfolio_app.admin.service;

import com.leaping.portfolio_app.admin.dto.AdminClientDetailResponse;
import com.leaping.portfolio_app.admin.dto.AdminHoldingPageResponse;
import com.leaping.portfolio_app.admin.dto.AdminClientPageResponse;
import com.leaping.portfolio_app.admin.dto.AdminClientSummaryResponse;
import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.CustomerRepository;
import com.leaping.portfolio_app.holdings.HoldingResponse;
import com.leaping.portfolio_app.holdings.HoldingService;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class AdminClientService {

        private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final PortfolioRepository portfolioRepository;
        private final HoldingService holdingService;

    public AdminClientService(
            CustomerRepository customerRepository,
                        PortfolioRepository portfolioRepository,
                        HoldingService holdingService
    ) {
        this.customerRepository = customerRepository;
                this.portfolioRepository = portfolioRepository;
                this.holdingService = holdingService;
    }

    @Transactional(readOnly = true)
    public AdminClientPageResponse getClients(
            String search,
            String status,
            int page,
            int size
    ) {
        String normalizedSearch = normalize(search);
        String normalizedStatus = normalize(status);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
        );

        Page<Customer> clientPage = customerRepository.findPage(
                normalizedSearch,
                normalizedStatus,
                PageRequest.of(safePage, safeSize)
        );

        List<AdminClientSummaryResponse> clients = clientPage
                .map(this::toSummaryResponse)
                .getContent();

        return new AdminClientPageResponse(
                clients,
                clientPage.getNumber(),
                clientPage.getSize(),
                clientPage.getTotalElements(),
                clientPage.getTotalPages(),
                clientPage.isFirst(),
                clientPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public AdminClientDetailResponse getClient(Long clientId) {

        Customer customer = customerRepository
                .findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Client not found"
                ));

        User user = customer.getUser();

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User record not found for client"
            );
        }

        Portfolio portfolio = portfolioRepository
                .findByCustomer_UserId(clientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Portfolio not found for client"
                ));

        return new AdminClientDetailResponse(
                customer.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getLastLogin(),
                portfolio.getPortfolioId(),
                portfolio.getCashBalanceUsd()
        );
    }

    @Transactional(readOnly = true)
    public AdminHoldingPageResponse getClientHoldings(
            Long clientId,
            int page,
            int size
    ) {
        customerRepository
                .findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Client not found"
                ));

        Portfolio portfolio = portfolioRepository
                .findByCustomer_UserId(clientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Portfolio not found for client"
                ));

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
        );

        Page<HoldingResponse> holdingPage = holdingService
                .getHoldingsByPortfolio(
                        portfolio.getPortfolioId(),
                        PageRequest.of(safePage, safeSize)
                );

        return new AdminHoldingPageResponse(
                holdingPage.getContent(),
                holdingPage.getNumber(),
                holdingPage.getSize(),
                holdingPage.getTotalElements(),
                holdingPage.getTotalPages(),
                holdingPage.isFirst(),
                holdingPage.isLast()
        );
    }

    private AdminClientSummaryResponse toSummaryResponse(
            Customer customer
    ) {
        User user = customer.getUser();

        return new AdminClientSummaryResponse(
                customer.getUserId(),
                user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null,
                user != null ? user.getEmail() : null,
                user != null ? user.getStatus() : null
        );
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

}