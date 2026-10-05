package com.leaping.portfolio_app.trade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Request DTO for placing a new buy/sell order
 */
public class PlaceOrderRequest {

    @JsonProperty("instrument_id")
    private Long instrumentId;

    @JsonProperty("order_action")
    private String orderAction;  // "BUY" or "SELL"

    @JsonProperty("order_type")
    private String orderType;    // "MARKET" or "LIMIT"

    @JsonProperty("quantity")
    private BigDecimal quantity;

    @JsonProperty("limit_price")
    private BigDecimal limitPrice;  // Only for LIMIT orders

    @JsonProperty("time_in_force")
    private String timeInForce;     // "DAY" or "GTC"

    public PlaceOrderRequest() {
    }

    public PlaceOrderRequest(
            Long instrumentId,
            String orderAction,
            String orderType,
            BigDecimal quantity,
            BigDecimal limitPrice,
            String timeInForce
    ) {
        this.instrumentId = instrumentId;
        this.orderAction = orderAction;
        this.orderType = orderType;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.timeInForce = timeInForce;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getOrderAction() {
        return orderAction;
    }

    public void setOrderAction(String orderAction) {
        this.orderAction = orderAction;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }
}
