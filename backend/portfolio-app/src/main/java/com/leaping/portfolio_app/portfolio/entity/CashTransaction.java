package com.leaping.portfolio_app.portfolio.entity;

import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.portfolio.enums.CashTransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cash_transactions")
public class CashTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_transaction_id")
    private Long cashTransactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id")
    private Trade trade;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private CashTransactionType transactionType;

    @Column(
        name = "amount_usd",
        nullable = false,
        precision = 20,
        scale = 2
    )
    private BigDecimal amountUsd;

    @Column(
        name = "balance_before_usd",
        nullable = false,
        precision = 20,
        scale = 2
    )
    private BigDecimal balanceBeforeUsd;

    @Column(
        name = "balance_after_usd",
        nullable = false,
        precision = 20,
        scale = 2
    )
    private BigDecimal balanceAfterUsd;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public CashTransaction() {
    }

    public CashTransaction(
            Portfolio portfolio,
            Trade trade,
            CashTransactionType transactionType,
            BigDecimal amountUsd,
            BigDecimal balanceBeforeUsd,
            BigDecimal balanceAfterUsd
    ) {
        this.portfolio = portfolio;
        this.trade = trade;
        this.transactionType = transactionType;
        this.amountUsd = amountUsd;
        this.balanceBeforeUsd = balanceBeforeUsd;
        this.balanceAfterUsd = balanceAfterUsd;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getCashTransactionId() {
        return cashTransactionId;
    }

    public void setCashTransactionId(Long cashTransactionId) {
        this.cashTransactionId = cashTransactionId;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public Trade getTrade() {
        return trade;
    }

    public void setTrade(Trade trade) {
        this.trade = trade;
    }

    public CashTransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(
            CashTransactionType transactionType
    ) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmountUsd() {
        return amountUsd;
    }

    public void setAmountUsd(BigDecimal amountUsd) {
        this.amountUsd = amountUsd;
    }

    public BigDecimal getBalanceBeforeUsd() {
        return balanceBeforeUsd;
    }

    public void setBalanceBeforeUsd(
            BigDecimal balanceBeforeUsd
    ) {
        this.balanceBeforeUsd = balanceBeforeUsd;
    }

    public BigDecimal getBalanceAfterUsd() {
        return balanceAfterUsd;
    }

    public void setBalanceAfterUsd(
            BigDecimal balanceAfterUsd
    ) {
        this.balanceAfterUsd = balanceAfterUsd;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}