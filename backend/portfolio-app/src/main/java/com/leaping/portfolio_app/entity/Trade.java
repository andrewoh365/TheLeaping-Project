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
@Table(name = "trade")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trade_id")
    private Long tradeId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(
        name = "execution_attempt_id",
        nullable = false,
        unique = true
    )
    private Long executionAttemptId;

    @Column(
        name = "quantity",
        nullable = false,
        precision = 30,
        scale = 10
    )
    private BigDecimal quantity;

    @Column(
        name = "execution_price",
        nullable = false,
        precision = 20,
        scale = 8
    )
    private BigDecimal executionPrice;

    @Column(
        name = "execution_price_currency_code",
        nullable = false,
        length = 10
    )
    private String executionPriceCurrencyCode;

    @Column(
        name = "exchange_rate_to_usd_at_execution",
        nullable = false,
        precision = 20,
        scale = 10
    )
    private BigDecimal exchangeRateToUsdAtExecution;

    @Column(
        name = "execution_price_usd",
        nullable = false,
        precision = 20,
        scale = 8
    )
    private BigDecimal executionPriceUsd;

    @Column(
        name = "total_usd_value",
        nullable = false,
        precision = 30,
        scale = 8
    )
    private BigDecimal totalUsdValue;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt;

    public Trade() {
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getExecutionAttemptId() {
        return executionAttemptId;
    }

    public void setExecutionAttemptId(Long executionAttemptId) {
        this.executionAttemptId = executionAttemptId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(BigDecimal executionPrice) {
        this.executionPrice = executionPrice;
    }

    public String getExecutionPriceCurrencyCode() {
        return executionPriceCurrencyCode;
    }

    public void setExecutionPriceCurrencyCode(
            String executionPriceCurrencyCode) {
        this.executionPriceCurrencyCode = executionPriceCurrencyCode;
    }

    public BigDecimal getExchangeRateToUsdAtExecution() {
        return exchangeRateToUsdAtExecution;
    }

    public void setExchangeRateToUsdAtExecution(
            BigDecimal exchangeRateToUsdAtExecution) {
        this.exchangeRateToUsdAtExecution =
                exchangeRateToUsdAtExecution;
    }

    public BigDecimal getExecutionPriceUsd() {
        return executionPriceUsd;
    }

    public void setExecutionPriceUsd(BigDecimal executionPriceUsd) {
        this.executionPriceUsd = executionPriceUsd;
    }

    public BigDecimal getTotalUsdValue() {
        return totalUsdValue;
    }

    public void setTotalUsdValue(BigDecimal totalUsdValue) {
        this.totalUsdValue = totalUsdValue;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(OffsetDateTime executedAt) {
        this.executedAt = executedAt;
    }
}