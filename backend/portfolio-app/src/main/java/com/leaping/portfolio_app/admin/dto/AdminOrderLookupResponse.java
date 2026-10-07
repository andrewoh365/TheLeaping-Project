package com.leaping.portfolio_app.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AdminOrderLookupResponse {

    private Long orderId;
    private Long tradeId;

    private Long clientId;
    private String firstName;
    private String lastName;

    private Long instrumentId;
    private String symbol;
    private String instrumentName;

    private String side;
    private BigDecimal quantity;
    private String status;

    private OffsetDateTime submittedAt;
    private OffsetDateTime executedAt;

    public AdminOrderLookupResponse() {
    }

    public AdminOrderLookupResponse(
            Long orderId,
            Long tradeId,
            Long clientId,
            String firstName,
            String lastName,
            Long instrumentId,
            String symbol,
            String instrumentName,
            String side,
            BigDecimal quantity,
            String status,
            OffsetDateTime submittedAt,
            OffsetDateTime executedAt
    ) {
        this.orderId = orderId;
        this.tradeId = tradeId;
        this.clientId = clientId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.instrumentId = instrumentId;
        this.symbol = symbol;
        this.instrumentName = instrumentName;
        this.side = side;
        this.quantity = quantity;
        this.status = status;
        this.submittedAt = submittedAt;
        this.executedAt = executedAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
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

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(OffsetDateTime executedAt) {
        this.executedAt = executedAt;
    }
}