package com.leaping.portfolio_app.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.leaping.portfolio_app.holdings.HoldingResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * PortfolioSummaryResponse provides a comprehensive portfolio overview with:
 * - Portfolio identifiers and cash balance
 * - Total portfolio value (cash + holdings at current market prices)
 * - Overall gain/loss (in USD and percentage)
 * - All holdings with real-time market prices and individual P&L
 * 
 * Used by dashboard and portfolio overview screens.
 */
public class PortfolioSummaryResponse {

    @JsonProperty("portfolio_id")
    private Long portfolioId;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("cash_balance_usd")
    private BigDecimal cashBalanceUsd;

    @JsonProperty("buying_power")
    private BigDecimal buyingPower;

    @JsonProperty("total_cost_basis")
    private BigDecimal totalCostBasis;

    @JsonProperty("total_current_value")
    private BigDecimal totalCurrentValue;

    @JsonProperty("total_gain_loss_usd")
    private BigDecimal totalGainLossUsd;

    @JsonProperty("total_gain_loss_percent")
    private BigDecimal totalGainLossPercent;

    @JsonProperty("holdings")
    private List<HoldingResponse> holdings;

    public PortfolioSummaryResponse() {
    }

    public PortfolioSummaryResponse(
            Long portfolioId,
            Long customerId,
            BigDecimal cashBalanceUsd,
            BigDecimal buyingPower,
            BigDecimal totalCostBasis,
            BigDecimal totalCurrentValue,
            BigDecimal totalGainLossUsd,
            BigDecimal totalGainLossPercent,
            List<HoldingResponse> holdings) {
        this.portfolioId = portfolioId;
        this.customerId = customerId;
        this.cashBalanceUsd = cashBalanceUsd;
        this.buyingPower = buyingPower;
        this.totalCostBasis = totalCostBasis;
        this.totalCurrentValue = totalCurrentValue;
        this.totalGainLossUsd = totalGainLossUsd;
        this.totalGainLossPercent = totalGainLossPercent;
        this.holdings = holdings;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getCashBalanceUsd() {
        return cashBalanceUsd;
    }

    public void setCashBalanceUsd(BigDecimal cashBalanceUsd) {
        this.cashBalanceUsd = cashBalanceUsd;
    }

    public BigDecimal getBuyingPower() {
        return buyingPower;
    }

    public void setBuyingPower(BigDecimal buyingPower) {
        this.buyingPower = buyingPower;
    }

    public BigDecimal getTotalCostBasis() {
        return totalCostBasis;
    }

    public void setTotalCostBasis(BigDecimal totalCostBasis) {
        this.totalCostBasis = totalCostBasis;
    }

    public BigDecimal getTotalCurrentValue() {
        return totalCurrentValue;
    }

    public void setTotalCurrentValue(BigDecimal totalCurrentValue) {
        this.totalCurrentValue = totalCurrentValue;
    }

    public BigDecimal getTotalGainLossUsd() {
        return totalGainLossUsd;
    }

    public void setTotalGainLossUsd(BigDecimal totalGainLossUsd) {
        this.totalGainLossUsd = totalGainLossUsd;
    }

    public BigDecimal getTotalGainLossPercent() {
        return totalGainLossPercent;
    }

    public void setTotalGainLossPercent(BigDecimal totalGainLossPercent) {
        this.totalGainLossPercent = totalGainLossPercent;
    }

    public List<HoldingResponse> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<HoldingResponse> holdings) {
        this.holdings = holdings;
    }
}
