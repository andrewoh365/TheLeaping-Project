package com.leaping.portfolio_app.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.leaping.portfolio_app.audit.enums.AuditEventType;
import com.leaping.portfolio_app.audit.service.AuditService;
import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.portfolio.dto.CashTransferRequest;
import com.leaping.portfolio_app.portfolio.dto.CashTransferResponse;
import com.leaping.portfolio_app.portfolio.entity.CashTransaction;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.enums.CashTransactionType;
import com.leaping.portfolio_app.portfolio.repository.CashTransactionRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class CashServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private CashTransactionRepository cashTransactionRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private CashService cashService;

    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio();
        portfolio.setPortfolioId(1L);
        portfolio.setCashBalanceUsd(new BigDecimal("1000.00"));

        Customer customer = new Customer();
        customer.setUserId(7L);
        User user = new User();
        user.setId(7L);
        customer.setUser(user);
        portfolio.setCustomer(customer);
    }

    private void stubPortfolioAndSave() {
        when(portfolioRepository.findByCustomer_UserId(7L)).thenReturn(Optional.of(portfolio));
        when(cashTransactionRepository.save(any(CashTransaction.class))).thenAnswer(invocation -> {
            CashTransaction transaction = invocation.getArgument(0);
            transaction.setCashTransactionId(55L);
            transaction.setCreatedAt(OffsetDateTime.parse("2026-10-14T10:15:30Z"));
            return transaction;
        });
    }

    @Test
    void depositShouldIncreaseBalanceAndRecordPositiveMovement() {
        stubPortfolioAndSave();

        CashTransferResponse response = cashService.transfer(7L, new CashTransferRequest("DEPOSIT", new BigDecimal("250.5")));

        assertEquals(new BigDecimal("1250.50"), portfolio.getCashBalanceUsd());
        assertEquals("DEPOSIT", response.transactionType());
        assertEquals(new BigDecimal("250.50"), response.amountUsd());
        assertEquals(new BigDecimal("1000.00"), response.balanceBeforeUsd());
        assertEquals(new BigDecimal("1250.50"), response.balanceAfterUsd());
        assertEquals(55L, response.transactionId());

        ArgumentCaptor<CashTransaction> captor = ArgumentCaptor.forClass(CashTransaction.class);
        verify(cashTransactionRepository).save(captor.capture());
        assertEquals(CashTransactionType.DEPOSIT, captor.getValue().getTransactionType());

        ArgumentCaptor<Map<String, Object>> detailsCaptor =
            ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordEvent(
            eq(AuditEventType.CASH_TRANSACTION_RECORDED),
            eq(portfolio.getCustomer().getUser()),
            isNull(),
            isNull(),
            isNull(),
            eq(captor.getValue()),
            detailsCaptor.capture()
        );

        assertEquals(7L, detailsCaptor.getValue().get("customerId"));
        assertEquals(1L, detailsCaptor.getValue().get("portfolioId"));
        assertEquals(55L, detailsCaptor.getValue().get("cashTransactionId"));
        assertEquals("DEPOSIT", detailsCaptor.getValue().get("transactionType"));
        assertEquals(new BigDecimal("250.50"), detailsCaptor.getValue().get("amountUsd"));
        assertEquals(new BigDecimal("1000.00"), detailsCaptor.getValue().get("balanceBeforeUsd"));
        assertEquals(new BigDecimal("1250.50"), detailsCaptor.getValue().get("balanceAfterUsd"));
        // The balance must be re-read under a row lock, the same lock trades take
        verify(entityManager).refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void withdrawShouldDecreaseBalanceAndRecordNegativeMovement() {
        stubPortfolioAndSave();

        CashTransferResponse response = cashService.transfer(7L, new CashTransferRequest("WITHDRAW", new BigDecimal("1000")));

        assertEquals(new BigDecimal("0.00"), portfolio.getCashBalanceUsd());
        assertEquals("WITHDRAWAL", response.transactionType());
        assertEquals(new BigDecimal("-1000.00"), response.amountUsd());
        verify(auditService).recordEvent(
            eq(AuditEventType.CASH_TRANSACTION_RECORDED),
            eq(portfolio.getCustomer().getUser()),
            isNull(),
            isNull(),
            isNull(),
            any(CashTransaction.class),
            any(Map.class)
        );
    }

    @Test
    void withdrawShouldBeRefusedWhenMoreThanBalance() {
        when(portfolioRepository.findByCustomer_UserId(7L)).thenReturn(Optional.of(portfolio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> cashService.transfer(7L, new CashTransferRequest("WITHDRAW", new BigDecimal("1000.01"))));

        assertEquals("You can't withdraw more than your cash balance of $1,000.00.", ex.getMessage());
        assertEquals(new BigDecimal("1000.00"), portfolio.getCashBalanceUsd());
        verify(cashTransactionRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void transferShouldNotAuditWhenCashTransactionPersistenceFails() {
        when(portfolioRepository.findByCustomer_UserId(7L)).thenReturn(Optional.of(portfolio));
        when(cashTransactionRepository.save(any(CashTransaction.class)))
                .thenThrow(new RuntimeException("cash transaction persistence failed"));

        assertThrows(
                RuntimeException.class,
                () -> cashService.transfer(
                        7L,
                        new CashTransferRequest("DEPOSIT", new BigDecimal("100.00"))
                )
        );

        verifyNoInteractions(auditService);
    }

    @Test
    void shouldRejectInvalidAmountsBeforeTouchingTheAccount() {
        assertEquals("Enter an amount greater than zero.", assertThrows(IllegalArgumentException.class,
            () -> cashService.transfer(7L, new CashTransferRequest("DEPOSIT", BigDecimal.ZERO))).getMessage());
        assertEquals("Enter an amount greater than zero.", assertThrows(IllegalArgumentException.class,
            () -> cashService.transfer(7L, new CashTransferRequest("DEPOSIT", null))).getMessage());
        assertEquals("Amounts can't have more than 2 decimal places.", assertThrows(IllegalArgumentException.class,
            () -> cashService.transfer(7L, new CashTransferRequest("DEPOSIT", new BigDecimal("10.005")))).getMessage());
        assertEquals("The most you can move at once is $1,000,000.00.", assertThrows(IllegalArgumentException.class,
            () -> cashService.transfer(7L, new CashTransferRequest("DEPOSIT", new BigDecimal("1000000.01")))).getMessage());
        assertEquals("Choose deposit or withdraw.", assertThrows(IllegalArgumentException.class,
            () -> cashService.transfer(7L, new CashTransferRequest("TRANSFER", BigDecimal.TEN))).getMessage());

        verifyNoInteractions(
            portfolioRepository,
            cashTransactionRepository,
            entityManager,
            auditService
        );
    }
}
