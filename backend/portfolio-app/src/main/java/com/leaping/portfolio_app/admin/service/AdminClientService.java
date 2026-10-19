
package com.leaping.portfolio_app.admin.service;

import com.leaping.portfolio_app.admin.dto.AdminClientDetailResponse;
import com.leaping.portfolio_app.admin.dto.AdminClientPageResponse;
import com.leaping.portfolio_app.admin.dto.AdminClientSummaryResponse;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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
    private final AuditEventRepository auditEventRepository;

    public AdminClientService(
            CustomerRepository customerRepository,
            PortfolioRepository portfolioRepository,
            HoldingService holdingService,
            AuditEventRepository auditEventRepository
    ) {
        this.customerRepository = customerRepository;
        this.portfolioRepository = portfolioRepository;
        this.holdingService = holdingService;
        this.auditEventRepository = auditEventRepository;
    }

    // Keep dev's paginated client search.
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

    // Keep dev's paginated client holdings.
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

    // Add Audit functionality from feature/audit.
    @Transactional(readOnly = true)
    public Page<AuditEventResponse> getClientActivity(
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

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE)
        );

        return auditEventRepository
                .findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(
                        clientId,
                        pageRequest
                )
                .map(this::toAuditEventResponse);
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

    // Convert AuditEvent entities to frontend-friendly DTOs.
    private AuditEventResponse toAuditEventResponse(
            AuditEvent event
    ) {
        return new AuditEventResponse(
                event.getAuditEventId(),
                event.getEventType(),
                event.getOccurredAt(),
                event.getUser() != null
                        ? event.getUser().getId()
                        : null,
                event.getOrder() != null
                        ? event.getOrder().getOrderId()
                        : null,
                event.getExecutionAttempt() != null
                        ? event.getExecutionAttempt().getExecutionAttemptId()
                        : null,
                event.getTrade() != null
                        ? event.getTrade().getTradeId()
                        : null,
                event.getCashTransaction() != null
                        ? event.getCashTransaction().getCashTransactionId()
                        : null,
                event.getEventDetails()
        );
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
