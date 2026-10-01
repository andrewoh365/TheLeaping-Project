package com.leaping.portfolio_app.instrument.dto;

import com.leaping.portfolio_app.instrument.entity.Instrument;

public class InstrumentDTO {
    private Long instrumentId;
    private String symbol;
    private String name;
    private String instrumentType;
    private String priceCurrencyCode;
    private Boolean isTradeable;
    private Boolean isActive;

    public InstrumentDTO() {
    }

    public InstrumentDTO(Instrument instrument) {
        this.instrumentId = instrument.getInstrumentId();
        this.symbol = instrument.getSymbol();
        this.name = instrument.getName();
        this.instrumentType = instrument.getInstrumentType() != null 
            ? instrument.getInstrumentType().toString() 
            : "UNKNOWN";
        this.priceCurrencyCode = instrument.getPriceCurrency() != null 
            && instrument.getPriceCurrency().getCurrencyCode() != null
            ? instrument.getPriceCurrency().getCurrencyCode() 
            : "USD";
        this.isTradeable = instrument.getIsTradeable() != null ? instrument.getIsTradeable() : false;
        this.isActive = instrument.getIsActive() != null ? instrument.getIsActive() : true;
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

    public String getPriceCurrencyCode() {
        return priceCurrencyCode;
    }

    public void setPriceCurrencyCode(String priceCurrencyCode) {
        this.priceCurrencyCode = priceCurrencyCode;
    }

    public Boolean getIsTradeable() {
        return isTradeable;
    }

    public void setIsTradeable(Boolean isTradeable) {
        this.isTradeable = isTradeable;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
