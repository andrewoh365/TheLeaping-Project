package com.leaping.portfolio_app.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AdminOrderDetailResponse {

    // Order
    private Long orderId;
    private String status;
    private String side;
    private String orderType;
    private String timeInForce;
    private BigDecimal quantity;
    private BigDecimal limitPriceUsd;

    // Client / portfolio
    private Long clientId;
    private String firstName;
    private String lastName;
    private Long portfolioId;

    // Instrument
    private Long instrumentId;
    private String symbol;
    private String instrumentName;

    // Order lifecycle timestamps
    private OffsetDateTime submittedAt;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime rejectedAt;
    private String rejectionReason;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime filledAt;

    // Trade - null when the order did not produce a trade
    private Long tradeId;
    private BigDecimal executionPrice;
    private String executionPriceCurrencyCode;
    private BigDecimal exchangeRateToUsdAtExecution;
    private BigDecimal executionPriceUsd;
    private BigDecimal totalUsdValue;
    private OffsetDateTime executedAt;

    public AdminOrderDetailResponse() {
    }

    public AdminOrderDetailResponse(
            Long orderId,
            String status,
            String side,
            String orderType,
            String timeInForce,
            BigDecimal quantity,
            BigDecimal limitPriceUsd,
            Long clientId,
            String firstName,
            String lastName,
            Long portfolioId,
            Long instrumentId,
            String symbol,
            String instrumentName,
            OffsetDateTime submittedAt,
            OffsetDateTime acceptedAt,
            OffsetDateTime rejectedAt,
            String rejectionReason,
            OffsetDateTime cancelledAt,
            OffsetDateTime filledAt,
            Long tradeId,
            BigDecimal executionPrice,
            String executionPriceCurrencyCode,
            BigDecimal exchangeRateToUsdAtExecution,
            BigDecimal executionPriceUsd,
            BigDecimal totalUsdValue,
            OffsetDateTime executedAt
    ) {
        this.orderId = orderId;
        this.status = status;
        this.side = side;
        this.orderType = orderType;
        this.timeInForce = timeInForce;
        this.quantity = quantity;
        this.limitPriceUsd = limitPriceUsd;
        this.clientId = clientId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.portfolioId = portfolioId;
        this.instrumentId = instrumentId;
        this.symbol = symbol;
        this.instrumentName = instrumentName;
        this.submittedAt = submittedAt;
        this.acceptedAt = acceptedAt;
        this.rejectedAt = rejectedAt;
        this.rejectionReason = rejectionReason;
        this.cancelledAt = cancelledAt;
        this.filledAt = filledAt;
        this.tradeId = tradeId;
        this.executionPrice = executionPrice;
        this.executionPriceCurrencyCode = executionPriceCurrencyCode;
        this.exchangeRateToUsdAtExecution = exchangeRateToUsdAtExecution;
        this.executionPriceUsd = executionPriceUsd;
        this.totalUsdValue = totalUsdValue;
        this.executedAt = executedAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLimitPriceUsd() {
        return limitPriceUsd;
    }

    public void setLimitPriceUsd(BigDecimal limitPriceUsd) {
        this.limitPriceUsd = limitPriceUsd;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(OffsetDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public OffsetDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(OffsetDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(OffsetDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public OffsetDateTime getFilledAt() {
        return filledAt;
    }

    public void setFilledAt(OffsetDateTime filledAt) {
        this.filledAt = filledAt;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
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
            String executionPriceCurrencyCode
    ) {
        this.executionPriceCurrencyCode = executionPriceCurrencyCode;
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
