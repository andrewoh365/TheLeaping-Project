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
@Table(name = "cryptos")
public class Crypto {

    @Id
    @Column(name = "instrument_id")
    private Long instrumentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @Column(name = "blockchain", length = 100)
    private String blockchain;

    public Crypto() {
    }

    public Crypto(
            Instrument instrument,
            String blockchain
    ) {
        this.instrument = instrument;
        this.blockchain = blockchain;
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

    public String getBlockchain() {
        return blockchain;
    }

    public void setBlockchain(String blockchain) {
        this.blockchain = blockchain;
    }
}