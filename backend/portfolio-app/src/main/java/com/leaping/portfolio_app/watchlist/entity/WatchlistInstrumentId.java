package com.leaping.portfolio_app.watchlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class WatchlistInstrumentId implements Serializable {

    @Column(name = "watchlist_id")
    private Long watchlistId;

    @Column(name = "instrument_id")
    private Long instrumentId;

    public WatchlistInstrumentId() {
    }

    public WatchlistInstrumentId(Long watchlistId, Long instrumentId) {
        this.watchlistId = watchlistId;
        this.instrumentId = instrumentId;
    }

    public Long getWatchlistId() {
        return watchlistId;
    }

    public void setWatchlistId(Long watchlistId) {
        this.watchlistId = watchlistId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof WatchlistInstrumentId that)) {
            return false;
        }

        return Objects.equals(watchlistId, that.watchlistId)
                && Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(watchlistId, instrumentId);
    }
}