package com.leaping.portfolio_app.holdings;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * DTO for displaying a holding (owned security)
 * Consolidated from HoldingDto and HoldingResponse to provide unified holdings data model
 */
public class HoldingResponse {

    @JsonProperty("holding_id")
    private Long holdingId;

    @JsonProperty("instrument_id")
    private Long instrumentId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("instrument_name")
    private String instrumentName;

    @JsonProperty("quantity")
    private BigDecimal quantity;

    @JsonProperty("average_cost_usd")
    private BigDecimal averageCostUsd;

    @JsonProperty("current_price")
    private BigDecimal currentPrice;

    @JsonProperty("current_value_usd")
    private BigDecimal currentValueUsd;

    @JsonProperty("gain_loss_usd")
    private BigDecimal gainLossUsd;

    @JsonProperty("gain_loss_percent")
    private BigDecimal gainLossPercent;

    public HoldingResponse() {
    }

    public HoldingResponse(Long holdingId, Long instrumentId, String symbol, String instrumentName,
                           BigDecimal quantity, BigDecimal averageCostUsd) {
        this.holdingId = holdingId;
        this.instrumentId = instrumentId;
        this.symbol = symbol;
        this.instrumentName = instrumentName;
        this.quantity = quantity;
        this.averageCostUsd = averageCostUsd;
    }

    public HoldingResponse(Long holdingId, Long instrumentId, String symbol, String instrumentName,
                           BigDecimal quantity, BigDecimal averageCostUsd, BigDecimal currentPrice,
                           BigDecimal currentValueUsd, BigDecimal gainLossUsd, BigDecimal gainLossPercent) {
        this.holdingId = holdingId;
        this.instrumentId = instrumentId;
        this.symbol = symbol;
        this.instrumentName = instrumentName;
        this.quantity = quantity;
        this.averageCostUsd = averageCostUsd;
        this.currentPrice = currentPrice;
        this.currentValueUsd = currentValueUsd;
        this.gainLossUsd = gainLossUsd;
        this.gainLossPercent = gainLossPercent;
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

    public BigDecimal getCurrentValueUsd() {
        return currentValueUsd;
    }

    public void setCurrentValueUsd(BigDecimal currentValueUsd) {
        this.currentValueUsd = currentValueUsd;
    }

    public BigDecimal getGainLossUsd() {
        return gainLossUsd;
    }

    public void setGainLossUsd(BigDecimal gainLossUsd) {
        this.gainLossUsd = gainLossUsd;
    }

    public BigDecimal getGainLossPercent() {
        return gainLossPercent;
    }

    public void setGainLossPercent(BigDecimal gainLossPercent) {
        this.gainLossPercent = gainLossPercent;
    }
}
