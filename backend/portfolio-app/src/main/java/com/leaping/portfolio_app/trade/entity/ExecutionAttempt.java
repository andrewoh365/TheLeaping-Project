package com.leaping.portfolio_app.trade.entity;

import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.trade.enums.ExecutionAttemptStatus;

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
@Table(name = "execution_attempts")
public class ExecutionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_attempt_id")
    private Long executionAttemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private TradeOrder order;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(
        name = "quoted_price",
        precision = 20,
        scale = 8
    )
    private BigDecimal quotedPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_currency_code")
    private Currency priceCurrency;

    @Column(
        name = "exchange_rate_to_usd",
        precision = 20,
        scale = 10
    )
    private BigDecimal exchangeRateToUsd;

    @Column(
        name = "quoted_price_usd",
        precision = 20,
        scale = 8
    )
    private BigDecimal quotedPriceUsd;

    @Column(name = "quote_timestamp")
    private OffsetDateTime quoteTimestamp;

    @Column(name = "source_type", length = 10)
    private String sourceType;

    @Column(name = "provider_name", length = 100)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_status", nullable = false, length = 20)
    private ExecutionAttemptStatus attemptStatus;

    @Column(name = "reason")
    private String reason;

    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt;

    public ExecutionAttempt() {
    }

    public ExecutionAttempt(
            TradeOrder order,
            Integer attemptNumber,
            ExecutionAttemptStatus attemptStatus
    ) {
        this.order = order;
        this.attemptNumber = attemptNumber;
        this.attemptStatus = attemptStatus;
    }

    @PrePersist
    protected void onCreate() {
        if (attemptNumber == null) {
            attemptNumber = 1;
        }

        if (attemptedAt == null) {
            attemptedAt = OffsetDateTime.now();
        }
    }

    public Long getExecutionAttemptId() {
        return executionAttemptId;
    }

    public void setExecutionAttemptId(Long executionAttemptId) {
        this.executionAttemptId = executionAttemptId;
    }

    public TradeOrder getOrder() {
        return order;
    }

    public void setOrder(TradeOrder order) {
        this.order = order;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public BigDecimal getQuotedPrice() {
        return quotedPrice;
    }

    public void setQuotedPrice(BigDecimal quotedPrice) {
        this.quotedPrice = quotedPrice;
    }

    public Currency getPriceCurrency() {
        return priceCurrency;
    }

    public void setPriceCurrency(Currency priceCurrency) {
        this.priceCurrency = priceCurrency;
    }

    public BigDecimal getExchangeRateToUsd() {
        return exchangeRateToUsd;
    }

    public void setExchangeRateToUsd(BigDecimal exchangeRateToUsd) {
        this.exchangeRateToUsd = exchangeRateToUsd;
    }

    public BigDecimal getQuotedPriceUsd() {
        return quotedPriceUsd;
    }

    public void setQuotedPriceUsd(BigDecimal quotedPriceUsd) {
        this.quotedPriceUsd = quotedPriceUsd;
    }

    public OffsetDateTime getQuoteTimestamp() {
        return quoteTimestamp;
    }

    public void setQuoteTimestamp(OffsetDateTime quoteTimestamp) {
        this.quoteTimestamp = quoteTimestamp;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public ExecutionAttemptStatus getAttemptStatus() {
        return attemptStatus;
    }

    public void setAttemptStatus(ExecutionAttemptStatus attemptStatus) {
        this.attemptStatus = attemptStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(OffsetDateTime attemptedAt) {
        this.attemptedAt = attemptedAt;
    }
}