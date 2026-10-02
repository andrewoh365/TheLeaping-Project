package com.leaping.portfolio_app.portfolio.dto;

import java.math.BigDecimal;

/* So far rest of the repo has been writing setters/getters/constructors
    instead of using Lombok Annotations for short writing, will just label
    equivalent Lombok in comments*/


public class HoldingResponse {

    private String instrumentSymbol; 
    private String instrumentName; 
    private BigDecimal quantity; 
    private BigDecimal averageCostUsd; 
    private BigDecimal currentPriceUsd; 
    private BigDecimal marketValueUsd; 
    private BigDecimal gainLossUsd; 
    private BigDecimal gainLossPercent;

    // @NoArgsConstructor
    public HoldingResponse(){

    }

    //AllArgsConstructor
     public HoldingResponse(String instrumentSymbol, String instrumentName,
                            BigDecimal quantity, BigDecimal averageCostUsd,
                            BigDecimal currentPriceUsd, BigDecimal marketValueUsd, 
                            BigDecimal gainLossUsd, BigDecimal gainLossPercent ) {
        this.instrumentSymbol = instrumentSymbol;
        this.instrumentName = instrumentName;
        this.quantity = quantity;
        this.averageCostUsd = averageCostUsd;
        this.currentPriceUsd = currentPriceUsd; 
        this.marketValueUsd = marketValueUsd; 
        this.gainLossUsd = gainLossUsd; 
        this.gainLossPercent = gainLossPercent;
    }

    //@Getter
    public String getInstrumentSymbol() {
        return instrumentSymbol;
    }

    //@Setter
    public void setInstrumentSymbol(String instrumentSymbol) {
        this.instrumentSymbol = instrumentSymbol;
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

    public BigDecimal getCurrentPriceUsd() {
        return currentPriceUsd;
    }

    public void setCurrentPriceUsd(BigDecimal currentPriceUsd) {
        this.currentPriceUsd = currentPriceUsd;
    }

    public BigDecimal getMarketValueUsd() {
        return marketValueUsd;
    }

    public void setMarketValueUsd(BigDecimal marketValueUsd) {
        this.marketValueUsd = marketValueUsd;
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
