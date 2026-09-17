package com.leaping.portfolio_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "price")
public class Price {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_id")
    private Long priceId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(
        name = "price",
        nullable = false,
        precision = 20,
        scale = 8
    )
    private BigDecimal price;

    @Column(
        name = "price_currency_code",
        nullable = false,
        length = 10
    )
    private String priceCurrencyCode;

    @Column(name = "price_timestamp", nullable = false)
    private OffsetDateTime priceTimestamp;

    @Column(
        name = "source_type",
        nullable = false,
        length = 10
    )
    private String sourceType;

    @Column(
        name = "provider_name",
        nullable = false,
        length = 100
    )
    private String providerName;

    public Price() {
    }

    public Long getPriceId() {
        return priceId;
    }

    public void setPriceId(Long priceId) {
        this.priceId = priceId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getPriceCurrencyCode() {
        return priceCurrencyCode;
    }

    public void setPriceCurrencyCode(String priceCurrencyCode) {
        this.priceCurrencyCode = priceCurrencyCode;
    }

    public OffsetDateTime getPriceTimestamp() {
        return priceTimestamp;
    }

    public void setPriceTimestamp(OffsetDateTime priceTimestamp) {
        this.priceTimestamp = priceTimestamp;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
}