package com.leaping.portfolio_app.instrument.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @Column(name = "instrument_id")
    private Long instrumentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @Column(name = "sector", length = 100)
    private String sector;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "country", length = 100)
    private String country;

    public Stock() {
    }

    public Stock(
            Instrument instrument,
            String sector,
            String industry,
            String country
    ) {
        this.instrument = instrument;
        this.sector = sector;
        this.industry = industry;
        this.country = country;
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

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}