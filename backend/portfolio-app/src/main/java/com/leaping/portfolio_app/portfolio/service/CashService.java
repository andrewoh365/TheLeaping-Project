package com.leaping.portfolio_app.portfolio.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leaping.portfolio_app.audit.enums.AuditEventType;
import com.leaping.portfolio_app.audit.service.AuditService;
import com.leaping.portfolio_app.portfolio.dto.CashTransferRequest;
import com.leaping.portfolio_app.portfolio.dto.CashTransferResponse;
import com.leaping.portfolio_app.portfolio.entity.CashTransaction;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.enums.CashTransactionType;
import com.leaping.portfolio_app.portfolio.repository.CashTransactionRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * Deposits and withdrawals against a client's cash balance.
 *
 * There is no bank integration in this phase, so these simply adjust the balance. Every
 * movement is still written to cash_transactions with the balance before and after,
 * so the balance can always be reconciled from the record (BR-14).
 */
@Service
public class CashService {

    /** Sanity cap per transaction; there is no real money behind these yet. */
    static final BigDecimal MAX_TRANSFER_USD = new BigDecimal("1000000.00");

    private final PortfolioRepository portfolioRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;

    public CashService(
            PortfolioRepository portfolioRepository,
            CashTransactionRepository cashTransactionRepository,
            EntityManager entityManager,
            AuditService auditService
    ) {
        this.portfolioRepository = portfolioRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.entityManager = entityManager;
        this.auditService = auditService;
    }

    /**
     * Deposit to or withdraw from the customer's cash balance.
     *
     * @param customerId The customer (User ID)
     * @param request    direction and amount
     * @throws IllegalArgumentException with a client-readable reason if the request is invalid
     */
    @Transactional
    public CashTransferResponse transfer(Long customerId, CashTransferRequest request) {
        CashTransactionType type = parseDirection(request.direction());
        BigDecimal amount = validateAmount(request.amount());

        Portfolio portfolio = portfolioRepository.findByCustomer_UserId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found for customer"));
        // Same lock trades take, so a withdrawal can't race a buy and overdraw the account.
        // refresh() re-reads the row rather than trusting a copy cached earlier in the request.
        entityManager.refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

        BigDecimal balanceBefore = portfolio.getCashBalanceUsd();
        BigDecimal signedAmount = type == CashTransactionType.DEPOSIT ? amount : amount.negate();
        BigDecimal balanceAfter = balanceBefore.add(signedAmount);

        if (balanceAfter.signum() < 0) {
            throw new IllegalArgumentException(String.format(
                "You can't withdraw more than your cash balance of $%,.2f.", balanceBefore));
        }

        portfolio.setCashBalanceUsd(balanceAfter);
        portfolio.setUpdatedAt(OffsetDateTime.now());
        portfolioRepository.save(portfolio);

        CashTransaction transaction = new CashTransaction();
        transaction.setPortfolio(portfolio);
        transaction.setTransactionType(type);
        transaction.setAmountUsd(signedAmount);
        transaction.setBalanceBeforeUsd(balanceBefore);
        transaction.setBalanceAfterUsd(balanceAfter);
        CashTransaction saved = cashTransactionRepository.save(transaction);

        auditService.recordEvent(
            AuditEventType.CASH_TRANSACTION_RECORDED,
            portfolio.getCustomer().getUser(),
            null,
            null,
            null,
            saved,
            cashTransactionAuditDetails(customerId, saved)
        );

        return new CashTransferResponse(
            saved.getCashTransactionId(),
            type.name(),
            signedAmount,
            balanceBefore,
            balanceAfter,
            saved.getCreatedAt());
    }

    private Map<String, Object> cashTransactionAuditDetails(
            Long customerId,
            CashTransaction transaction
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("customerId", customerId);
        details.put("portfolioId", transaction.getPortfolio().getPortfolioId());
        details.put("cashTransactionId", transaction.getCashTransactionId());
        details.put("transactionType", transaction.getTransactionType().name());
        details.put("amountUsd", transaction.getAmountUsd());
        details.put("balanceBeforeUsd", transaction.getBalanceBeforeUsd());
        details.put("balanceAfterUsd", transaction.getBalanceAfterUsd());
        details.put("createdAt", transaction.getCreatedAt());
        return details;
    }

    private CashTransactionType parseDirection(String direction) {
        if ("DEPOSIT".equalsIgnoreCase(direction)) {
            return CashTransactionType.DEPOSIT;
        }
        if ("WITHDRAW".equalsIgnoreCase(direction) || "WITHDRAWAL".equalsIgnoreCase(direction)) {
            return CashTransactionType.WITHDRAWAL;
        }
        throw new IllegalArgumentException("Choose deposit or withdraw.");
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return reject("Enter an amount greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            return reject("Amounts can't have more than 2 decimal places.");
        }
        if (amount.compareTo(MAX_TRANSFER_USD) > 0) {
            return reject(String.format("The most you can move at once is $%,.2f.", MAX_TRANSFER_USD));
        }
        return amount.setScale(2);
    }

    private static BigDecimal reject(String message) {
        throw new IllegalArgumentException(message);
    }
}
