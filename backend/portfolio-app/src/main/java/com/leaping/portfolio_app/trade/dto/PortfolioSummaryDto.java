package com.leaping.portfolio_app.trade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * DTO for portfolio summary/overview
 */
public class PortfolioSummaryDto {

    @JsonProperty("portfolio_id")
    private Long portfolioId;

    @JsonProperty("cash_balance_usd")
    private BigDecimal cashBalanceUsd;

    @JsonProperty("total_holdings_value_usd")
    private BigDecimal totalHoldingsValueUsd;

    @JsonProperty("total_portfolio_value_usd")
    private BigDecimal totalPortfolioValueUsd;

    @JsonProperty("total_gain_loss_usd")
    private BigDecimal totalGainLossUsd;

    @JsonProperty("total_gain_loss_percent")
    private BigDecimal totalGainLossPercent;

    @JsonProperty("buying_power_usd")
    private BigDecimal buyingPowerUsd;  // Available cash for purchases

    public PortfolioSummaryDto() {
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public BigDecimal getCashBalanceUsd() {
        return cashBalanceUsd;
    }

    public void setCashBalanceUsd(BigDecimal cashBalanceUsd) {
        this.cashBalanceUsd = cashBalanceUsd;
    }

    public BigDecimal getTotalHoldingsValueUsd() {
        return totalHoldingsValueUsd;
    }

    public void setTotalHoldingsValueUsd(BigDecimal totalHoldingsValueUsd) {
        this.totalHoldingsValueUsd = totalHoldingsValueUsd;
    }

    public BigDecimal getTotalPortfolioValueUsd() {
        return totalPortfolioValueUsd;
    }

    public void setTotalPortfolioValueUsd(BigDecimal totalPortfolioValueUsd) {
        this.totalPortfolioValueUsd = totalPortfolioValueUsd;
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

    public BigDecimal getBuyingPowerUsd() {
        return buyingPowerUsd;
    }

    public void setBuyingPowerUsd(BigDecimal buyingPowerUsd) {
        this.buyingPowerUsd = buyingPowerUsd;
    }
}
