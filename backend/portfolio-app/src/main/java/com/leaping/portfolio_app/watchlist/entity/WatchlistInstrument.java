package com.leaping.portfolio_app.watchlist.entity;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "watchlist_instruments")
public class WatchlistInstrument {

    @EmbeddedId
    private WatchlistInstrumentId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("watchlistId")
    @JoinColumn(name = "watchlist_id", nullable = false)
    private Watchlist watchlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("instrumentId")
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    public WatchlistInstrument() {
    }

    public WatchlistInstrument(
            Watchlist watchlist,
            Instrument instrument
    ) {
        this.watchlist = watchlist;
        this.instrument = instrument;

        this.id = new WatchlistInstrumentId(
                watchlist.getWatchlistId(),
                instrument.getInstrumentId()
        );
    }

    public WatchlistInstrumentId getId() {
        return id;
    }

    public void setId(WatchlistInstrumentId id) {
        this.id = id;
    }

    public Watchlist getWatchlist() {
        return watchlist;
    }

    public void setWatchlist(Watchlist watchlist) {
        this.watchlist = watchlist;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
    }
}