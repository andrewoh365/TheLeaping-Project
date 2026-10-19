package com.leaping.portfolio_app.trade.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pre-trade preview shown on the trade ticket before the client submits (BR-13).
 *
 * Built from the same rules as order placement, so if can_submit is false the
 * order would be refused, and message explains why in plain language.
 */
public class OrderPreviewResponse {

    @JsonProperty("instrument_id")
    private Long instrumentId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("name")
    private String name;

    @JsonProperty("instrument_type")
    private String instrumentType;

    @JsonProperty("market_name")
    private String marketName;

    @JsonProperty("order_action")
    private String orderAction;

    @JsonProperty("quantity")
    private BigDecimal quantity;

    /** Indicative price for this side: the ask when buying, the bid when selling. Not guaranteed. */
    @JsonProperty("indicative_price_usd")
    private BigDecimal indicativePriceUsd;

    @JsonProperty("bid_usd")
    private BigDecimal bidUsd;

    @JsonProperty("ask_usd")
    private BigDecimal askUsd;

    @JsonProperty("spread_usd")
    private BigDecimal spreadUsd;

    @JsonProperty("estimated_total_usd")
    private BigDecimal estimatedTotalUsd;

    @JsonProperty("cash_available_usd")
    private BigDecimal cashAvailableUsd;

    @JsonProperty("quantity_owned")
    private BigDecimal quantityOwned;

    @JsonProperty("max_quantity")
    private BigDecimal maxQuantity;

    @JsonProperty("tradable")
    private boolean tradable;

    @JsonProperty("market_open")
    private boolean marketOpen;

    @JsonProperty("can_submit")
    private boolean canSubmit;

    @JsonProperty("message")
    private String message;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public void setInstrumentType(String instrumentType) {
        this.instrumentType = instrumentType;
    }

    public String getMarketName() {
        return marketName;
    }

    public void setMarketName(String marketName) {
        this.marketName = marketName;
    }

    public String getOrderAction() {
        return orderAction;
    }

    public void setOrderAction(String orderAction) {
        this.orderAction = orderAction;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getIndicativePriceUsd() {
        return indicativePriceUsd;
    }

    public void setIndicativePriceUsd(BigDecimal indicativePriceUsd) {
        this.indicativePriceUsd = indicativePriceUsd;
    }

    public BigDecimal getBidUsd() {
        return bidUsd;
    }

    public void setBidUsd(BigDecimal bidUsd) {
        this.bidUsd = bidUsd;
    }

    public BigDecimal getAskUsd() {
        return askUsd;
    }

    public void setAskUsd(BigDecimal askUsd) {
        this.askUsd = askUsd;
    }

    public BigDecimal getSpreadUsd() {
        return spreadUsd;
    }

    public void setSpreadUsd(BigDecimal spreadUsd) {
        this.spreadUsd = spreadUsd;
    }

    public BigDecimal getEstimatedTotalUsd() {
        return estimatedTotalUsd;
    }

    public void setEstimatedTotalUsd(BigDecimal estimatedTotalUsd) {
        this.estimatedTotalUsd = estimatedTotalUsd;
    }

    public BigDecimal getCashAvailableUsd() {
        return cashAvailableUsd;
    }

    public void setCashAvailableUsd(BigDecimal cashAvailableUsd) {
        this.cashAvailableUsd = cashAvailableUsd;
    }

    public BigDecimal getQuantityOwned() {
        return quantityOwned;
    }

    public void setQuantityOwned(BigDecimal quantityOwned) {
        this.quantityOwned = quantityOwned;
    }

    public BigDecimal getMaxQuantity() {
        return maxQuantity;
    }

    public void setMaxQuantity(BigDecimal maxQuantity) {
        this.maxQuantity = maxQuantity;
    }

    public boolean isTradable() {
        return tradable;
    }

    public void setTradable(boolean tradable) {
        this.tradable = tradable;
    }

    public boolean isMarketOpen() {
        return marketOpen;
    }

    public void setMarketOpen(boolean marketOpen) {
        this.marketOpen = marketOpen;
    }

    public boolean isCanSubmit() {
        return canSubmit;
    }

    public void setCanSubmit(boolean canSubmit) {
        this.canSubmit = canSubmit;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
