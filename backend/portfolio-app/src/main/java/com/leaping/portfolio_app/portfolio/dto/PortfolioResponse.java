package com.leaping.portfolio_app.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;
import com.leaping.portfolio_app.holdings.HoldingResponse;

public class PortfolioResponse {
    private Long portfolioId;
    private BigDecimal cashBalanceUsd;
    private List<HoldingResponse> holdings;

    //@NoArgsConstructor
    public PortfolioResponse() {
    }

    //@AllArgsConstructor
    public PortfolioResponse(Long portfolioId, BigDecimal cashBalanceUsd, List<HoldingResponse> holdings) {
        this.portfolioId = portfolioId;
        this.cashBalanceUsd = cashBalanceUsd;
        this.holdings = holdings;
    }

    //@Getter
    public Long getPortfolioId() {
        return portfolioId;
    }
    
    //@Setter
    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public BigDecimal getCashBalanceUsd() {
        return cashBalanceUsd;
    }

    public void setCashBalanceUsd(BigDecimal cashBalanceUsd) {
        this.cashBalanceUsd = cashBalanceUsd;
    }

    public List<HoldingResponse> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<HoldingResponse> holdings) {
        this.holdings = holdings;
    }


}







