package com.leaping.portfolio_app.trade.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for the authenticated client's current portfolio overview.
 */
public class PortfolioOverviewResponse {

    @JsonProperty("summary")
    private PortfolioSummaryDto summary;

    @JsonProperty("holdings")
    private List<HoldingDto> holdings;

    @JsonProperty("last_executed_trade_at")
    private OffsetDateTime lastExecutedTradeAt;

    public PortfolioSummaryDto getSummary() {
        return summary;
    }

    public void setSummary(PortfolioSummaryDto summary) {
        this.summary = summary;
    }

    public List<HoldingDto> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<HoldingDto> holdings) {
        this.holdings = holdings;
    }

    public OffsetDateTime getLastExecutedTradeAt() {
        return lastExecutedTradeAt;
    }

    public void setLastExecutedTradeAt(OffsetDateTime lastExecutedTradeAt) {
        this.lastExecutedTradeAt = lastExecutedTradeAt;
    }
}