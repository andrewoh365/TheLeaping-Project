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
@Table(name = "execution_attempt")
public class ExecutionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_attempt_id")
    private Long executionAttemptId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Column(
        name = "quoted_price",
        precision = 20,
        scale = 8
    )
    private BigDecimal quotedPrice;

    @Column(name = "price_currency_code", length = 10)
    private String priceCurrencyCode;

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

    @Column(name = "attempt_status", nullable = false, length = 20)
    private String attemptStatus;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(
        name = "attempted_at",
        insertable = false,
        updatable = false
    )
    private OffsetDateTime attemptedAt;

    public ExecutionAttempt() {
    }

    public Long getExecutionAttemptId() {
        return executionAttemptId;
    }

    public void setExecutionAttemptId(Long executionAttemptId) {
        this.executionAttemptId = executionAttemptId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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

    public String getPriceCurrencyCode() {
        return priceCurrencyCode;
    }

    public void setPriceCurrencyCode(String priceCurrencyCode) {
        this.priceCurrencyCode = priceCurrencyCode;
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

    public String getAttemptStatus() {
        return attemptStatus;
    }

    public void setAttemptStatus(String attemptStatus) {
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
}