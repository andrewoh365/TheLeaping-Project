package com.leaping.portfolio_app.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AdminHoldingResponse {

    private Long holdingId;
    private Long instrumentId;

    private String symbol;
    private String instrumentName;
    private String instrumentType;

    private BigDecimal quantity;
    private BigDecimal averageCostUsd;

    private BigDecimal currentPrice;
    private String priceCurrencyCode;
    private BigDecimal currentValueUsd;

    private OffsetDateTime updatedAt;

    public AdminHoldingResponse() {
    }

    public AdminHoldingResponse(
            Long holdingId,
            Long instrumentId,
            String symbol,
            String instrumentName,
            String instrumentType,
            BigDecimal quantity,
            BigDecimal averageCostUsd,
            BigDecimal currentPrice,
            String priceCurrencyCode,
            BigDecimal currentValueUsd,
            OffsetDateTime updatedAt
    ) {
        this.holdingId = holdingId;
        this.instrumentId = instrumentId;
        this.symbol = symbol;
        this.instrumentName = instrumentName;
        this.instrumentType = instrumentType;
        this.quantity = quantity;
        this.averageCostUsd = averageCostUsd;
        this.currentPrice = currentPrice;
        this.priceCurrencyCode = priceCurrencyCode;
        this.currentValueUsd = currentValueUsd;
        this.updatedAt = updatedAt;
    }

    public Long getHoldingId() {
        return holdingId;
    }

    public void setHoldingId(Long holdingId) {
        this.holdingId = holdingId;
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

    public String getInstrumentType() {
        return instrumentType;
    }

    public void setInstrumentType(String instrumentType) {
        this.instrumentType = instrumentType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAverageCostUsd() {
        return averageCostUsd;
    }

    public void setAverageCostUsd(BigDecimal averageCostUsd) {
        this.averageCostUsd = averageCostUsd;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getPriceCurrencyCode() {
        return priceCurrencyCode;
    }

    public void setPriceCurrencyCode(String priceCurrencyCode) {
        this.priceCurrencyCode = priceCurrencyCode;
    }

    public BigDecimal getCurrentValueUsd() {
        return currentValueUsd;
    }

    public void setCurrentValueUsd(BigDecimal currentValueUsd) {
        this.currentValueUsd = currentValueUsd;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}