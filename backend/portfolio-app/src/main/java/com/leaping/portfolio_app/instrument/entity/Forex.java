package com.leaping.portfolio_app.instrument.entity;

import com.leaping.portfolio_app.market.entity.Currency;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "forexes",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_forex_pair",
            columnNames = {
                "base_currency_code",
                "quote_currency_code"
            }
        )
    }
)
public class Forex {

    @Id
    private Long instrumentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "base_currency_code",
        nullable = false
    )
    private Currency baseCurrency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "quote_currency_code",
        nullable = false
    )
    private Currency quoteCurrency;

    public Forex() {
    }

    public Forex(
            Instrument instrument,
            Currency baseCurrency,
            Currency quoteCurrency
    ) {
        this.instrument = instrument;
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
    }

    public Currency getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(Currency baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public Currency getQuoteCurrency() {
        return quoteCurrency;
    }

    public void setQuoteCurrency(Currency quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }
}