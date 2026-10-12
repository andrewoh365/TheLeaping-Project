package com.leaping.portfolio_app.trade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response DTO for completed trade confirmation
 */
public class TradeConfirmationResponse {

    @JsonProperty("trade_id")
    private Long tradeId;

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("instrument_symbol")
    private String instrumentSymbol;

    @JsonProperty("order_action")
    private String orderAction;  // BUY or SELL

    @JsonProperty("executed_quantity")
    private BigDecimal executedQuantity;

    @JsonProperty("execution_price")
    private BigDecimal executionPrice;

    @JsonProperty("total_usd_value")
    private BigDecimal totalUsdValue;

    @JsonProperty("executed_at")
    private OffsetDateTime executedAt;

    public TradeConfirmationResponse() {
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

    public String getInstrumentSymbol() {
        return instrumentSymbol;
    }

    public void setInstrumentSymbol(String instrumentSymbol) {
        this.instrumentSymbol = instrumentSymbol;
    }

    public String getOrderAction() {
        return orderAction;
    }

    public void setOrderAction(String orderAction) {
        this.orderAction = orderAction;
    }

    public BigDecimal getExecutedQuantity() {
        return executedQuantity;
    }

    public void setExecutedQuantity(BigDecimal executedQuantity) {
        this.executedQuantity = executedQuantity;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(BigDecimal executionPrice) {
        this.executionPrice = executionPrice;
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
