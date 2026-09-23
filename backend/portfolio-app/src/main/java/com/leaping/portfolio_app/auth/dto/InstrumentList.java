package com.leaping.portfolio_app.dto;

import java.util.List;

public class InstrumentList {
    private String instrumentId;
    private String symbol;
     private String instrumentType;
    private String priceCurrencyCode;


    public InstrumentList(String instrumentId, String symbol, String instrumentType, String priceCurrencyCode) {
        this.instrumentId = instrumentId;
        this.symbol = symbol;
        this.instrumentType = instrumentType;
        this.priceCurrencyCode = priceCurrencyCode;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(String instrumentId) {
        this.instrumentId = instrumentId;
    }
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
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


}