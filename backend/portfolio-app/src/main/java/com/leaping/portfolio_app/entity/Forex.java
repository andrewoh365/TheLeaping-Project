package com.leaping.portfolio_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "forex")
public class Forex {

    @Id
    @Column(name = "instrument_id")
    private Long instrumentId;

    @Column(name = "base_currency_code", nullable = false, length = 10)
    private String baseCurrencyCode;

    @Column(name = "quote_currency_code", nullable = false, length = 10)
    private String quoteCurrencyCode;

    public Forex() {
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public void setBaseCurrencyCode(String baseCurrencyCode) {
        this.baseCurrencyCode = baseCurrencyCode;
    }

    public String getQuoteCurrencyCode() {
        return quoteCurrencyCode;
    }

    public void setQuoteCurrencyCode(String quoteCurrencyCode) {
        this.quoteCurrencyCode = quoteCurrencyCode;
    }
}