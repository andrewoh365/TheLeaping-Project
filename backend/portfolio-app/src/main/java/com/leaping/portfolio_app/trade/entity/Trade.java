package com.leaping.portfolio_app.trade.entity;

import com.leaping.portfolio_app.market.entity.Currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trade_id")
    private Long tradeId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        unique = true
    )
    private TradeOrder order;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "execution_attempt_id",
        nullable = false,
        unique = true
    )
    private ExecutionAttempt executionAttempt;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "execution_price_currency_code",
        nullable = false
    )
    private Currency executionPriceCurrency;

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

    public Trade(
            TradeOrder order,
            ExecutionAttempt executionAttempt,
            BigDecimal quantity,
            BigDecimal executionPrice,
            Currency executionPriceCurrency,
            BigDecimal exchangeRateToUsdAtExecution,
            BigDecimal executionPriceUsd,
            BigDecimal totalUsdValue
    ) {
        this.order = order;
        this.executionAttempt = executionAttempt;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.executionPriceCurrency = executionPriceCurrency;
        this.exchangeRateToUsdAtExecution =
                exchangeRateToUsdAtExecution;
        this.executionPriceUsd = executionPriceUsd;
        this.totalUsdValue = totalUsdValue;
    }

    @PrePersist
    protected void onCreate() {
        if (executedAt == null) {
            executedAt = OffsetDateTime.now();
        }
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public TradeOrder getOrder() {
        return order;
    }

    public void setOrder(TradeOrder order) {
        this.order = order;
    }

    public ExecutionAttempt getExecutionAttempt() {
        return executionAttempt;
    }

    public void setExecutionAttempt(
            ExecutionAttempt executionAttempt
    ) {
        this.executionAttempt = executionAttempt;
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

    public Currency getExecutionPriceCurrency() {
        return executionPriceCurrency;
    }

    public void setExecutionPriceCurrency(
            Currency executionPriceCurrency
    ) {
        this.executionPriceCurrency = executionPriceCurrency;
    }

    public BigDecimal getExchangeRateToUsdAtExecution() {
        return exchangeRateToUsdAtExecution;
    }

    public void setExchangeRateToUsdAtExecution(
            BigDecimal exchangeRateToUsdAtExecution
    ) {
        this.exchangeRateToUsdAtExecution =
                exchangeRateToUsdAtExecution;
    }

    public BigDecimal getExecutionPriceUsd() {
        return executionPriceUsd;
    }

    public void setExecutionPriceUsd(
            BigDecimal executionPriceUsd
    ) {
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