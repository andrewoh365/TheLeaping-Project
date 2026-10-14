package com.leaping.portfolio_app.admin.service;

import com.leaping.portfolio_app.admin.dto.AdminClientDetailResponse;
import com.leaping.portfolio_app.admin.dto.AdminClientSummaryResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdminClientService {

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

    @Transactional(readOnly = true)
    public List<AdminClientSummaryResponse> getClients(
            String search,
            String status
    ) {
        String normalizedSearch = normalize(search);
        String normalizedStatus = normalize(status);

        return customerRepository.findAll()
                .stream()
                .filter(customer ->
                        matchesSearch(customer, normalizedSearch))
                .filter(customer ->
                        matchesStatus(customer, normalizedStatus))
                .map(this::toSummaryResponse)
                .sorted(
                        Comparator.comparing(
                                AdminClientSummaryResponse::getClientId
                        )
                )
                .toList();
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
    public List<HoldingResponse> getClientHoldings(Long clientId) {

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

        return holdingService.getHoldingsByPortfolio(
                portfolio.getPortfolioId()
        );
    }

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
                Math.min(Math.max(size, 1), 100)
        );

        return auditEventRepository
                .findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(
                        clientId,
                        pageRequest
                )
                .map(this::toAuditEventResponse);
    }

    private boolean matchesSearch(
            Customer customer,
            String search
    ) {
        if (search.isEmpty()) {
            return true;
        }

        User user = customer.getUser();

        if (user == null) {
            return false;
        }

        String clientId =
                customer.getUserId() == null
                        ? ""
                        : customer.getUserId().toString();

        String firstName = safeLower(user.getFirstName());
        String lastName = safeLower(user.getLastName());
        String email = safeLower(user.getEmail());

        String fullName =
                (firstName + " " + lastName).trim();

        return clientId.contains(search)
                || firstName.contains(search)
                || lastName.contains(search)
                || fullName.contains(search)
                || email.contains(search);
    }

    private boolean matchesStatus(
            Customer customer,
            String status
    ) {
        if (status.isEmpty()) {
            return true;
        }

        User user = customer.getUser();

        if (user == null || user.getStatus() == null) {
            return false;
        }

        return user.getStatus()
                .equalsIgnoreCase(status);
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

    private AuditEventResponse toAuditEventResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getAuditEventId(),
                event.getEventType(),
                event.getOccurredAt(),
                event.getUser() != null ? event.getUser().getId() : null,
                event.getOrder() != null ? event.getOrder().getOrderId() : null,
                event.getExecutionAttempt() != null
                        ? event.getExecutionAttempt().getExecutionAttemptId()
                        : null,
                event.getTrade() != null ? event.getTrade().getTradeId() : null,
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

    private String safeLower(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT);
    }
}