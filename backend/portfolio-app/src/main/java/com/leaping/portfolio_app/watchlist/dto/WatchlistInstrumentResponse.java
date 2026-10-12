
package com.leaping.portfolio_app.watchlist.dto;

import com.leaping.portfolio_app.instrument.enums.InstrumentType;

public class WatchlistInstrumentResponse {

    private Long instrumentId;

    private String symbol;

    private String name;

    private InstrumentType instrumentType;

    public WatchlistInstrumentResponse() {
    }

    public WatchlistInstrumentResponse(
            Long instrumentId,
            String symbol,
            String name,
            InstrumentType instrumentType
    ) {
        this.instrumentId = instrumentId;
        this.symbol = symbol;
        this.name = name;
        this.instrumentType = instrumentType;
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

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    public void setInstrumentType(InstrumentType instrumentType) {
        this.instrumentType = instrumentType;
    }
}
