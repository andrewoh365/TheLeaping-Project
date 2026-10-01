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

    // @NoArgsConstructor
    public HoldingResponse(){

    }

    //AllArgsConstructor
     public HoldingResponse(String instrumentSymbol, String instrumentName,
                            BigDecimal quantity, BigDecimal averageCostUsd) {
        this.instrumentSymbol = instrumentSymbol;
        this.instrumentName = instrumentName;
        this.quantity = quantity;
        this.averageCostUsd = averageCostUsd;
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


}
