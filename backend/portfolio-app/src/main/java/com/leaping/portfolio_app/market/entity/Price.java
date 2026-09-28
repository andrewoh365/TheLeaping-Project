package com.leaping.portfolio_app.market.entity;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.enums.PriceSourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "prices")
public class Price {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_id")
    private Long priceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(
        name = "price",
        nullable = false,
        precision = 20,
        scale = 8
    )
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "price_currency_code",
        nullable = false
    )
    private Currency priceCurrency;

    @Column(name = "price_timestamp", nullable = false)
    private OffsetDateTime priceTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    private PriceSourceType sourceType;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    public Price() {
    }

    public Price(
            Instrument instrument,
            BigDecimal price,
            Currency priceCurrency,
            OffsetDateTime priceTimestamp,
            PriceSourceType sourceType,
            String providerName
    ) {
        this.instrument = instrument;
        this.price = price;
        this.priceCurrency = priceCurrency;
        this.priceTimestamp = priceTimestamp;
        this.sourceType = sourceType;
        this.providerName = providerName;
    }

    public Long getPriceId() {
        return priceId;
    }

    public void setPriceId(Long priceId) {
        this.priceId = priceId;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Currency getPriceCurrency() {
        return priceCurrency;
    }

    public void setPriceCurrency(Currency priceCurrency) {
        this.priceCurrency = priceCurrency;
    }

    public OffsetDateTime getPriceTimestamp() {
        return priceTimestamp;
    }

    public void setPriceTimestamp(
            OffsetDateTime priceTimestamp
    ) {
        this.priceTimestamp = priceTimestamp;
    }

    public PriceSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(PriceSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
}