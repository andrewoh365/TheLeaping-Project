package com.leaping.portfolio_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "currency")
public class Currency {

    @Id
    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(name = "currency_name", nullable = false, length = 100)
    private String currencyName;

    @Column(name = "currency_symbol", length = 10)
    private String currencySymbol;

    @Column(
        name = "current_exchange_rate_to_usd",
        precision = 20,
        scale = 10
    )
    private BigDecimal currentExchangeRateToUsd;

    @Column(name = "exchange_rate_updated_at")
    private OffsetDateTime exchangeRateUpdatedAt;

    public Currency() {
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public void setCurrencyName(String currencyName) {
        this.currencyName = currencyName;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public BigDecimal getCurrentExchangeRateToUsd() {
        return currentExchangeRateToUsd;
    }

    public void setCurrentExchangeRateToUsd(BigDecimal currentExchangeRateToUsd) {
        this.currentExchangeRateToUsd = currentExchangeRateToUsd;
    }

    public OffsetDateTime getExchangeRateUpdatedAt() {
        return exchangeRateUpdatedAt;
    }

    public void setExchangeRateUpdatedAt(OffsetDateTime exchangeRateUpdatedAt) {
        this.exchangeRateUpdatedAt = exchangeRateUpdatedAt;
    }
}