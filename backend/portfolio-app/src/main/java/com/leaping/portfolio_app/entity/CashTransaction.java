package com.leaping.portfolio_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cash_transaction")
public class CashTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_transaction_id")
    private Long cashTransactionId;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "trade_id")
    private Long tradeId;

    @Column(
        name = "transaction_type",
        nullable = false,
        length = 30
    )
    private String transactionType;

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

    @Column(
        name = "created_at",
        insertable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    public CashTransaction() {
    }

    public Long getCashTransactionId() {
        return cashTransactionId;
    }

    public void setCashTransactionId(Long cashTransactionId) {
        this.cashTransactionId = cashTransactionId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
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

    public void setBalanceBeforeUsd(BigDecimal balanceBeforeUsd) {
        this.balanceBeforeUsd = balanceBeforeUsd;
    }

    public BigDecimal getBalanceAfterUsd() {
        return balanceAfterUsd;
    }

    public void setBalanceAfterUsd(BigDecimal balanceAfterUsd) {
        this.balanceAfterUsd = balanceAfterUsd;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}