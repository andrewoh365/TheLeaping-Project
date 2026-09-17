package com.leaping.portfolio_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "watchlist_instrument")
public class WatchlistInstrument {

    @EmbeddedId
    private WatchlistInstrumentId id;

    public WatchlistInstrument() {
    }

    public WatchlistInstrumentId getId() {
        return id;
    }

    public void setId(WatchlistInstrumentId id) {
        this.id = id;
    }

    @Embeddable
    public static class WatchlistInstrumentId implements Serializable {

        @Column(name = "watchlist_id", nullable = false)
        private Long watchlistId;

        @Column(name = "instrument_id", nullable = false)
        private Long instrumentId;

        public WatchlistInstrumentId() {
        }

        public WatchlistInstrumentId(
                Long watchlistId,
                Long instrumentId) {

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
        public boolean equals(Object object) {

            if (this == object) {
                return true;
            }

            if (!(object instanceof WatchlistInstrumentId)) {
                return false;
            }

            WatchlistInstrumentId other =
                    (WatchlistInstrumentId) object;

            return Objects.equals(
                        watchlistId,
                        other.watchlistId)
                    &&
                    Objects.equals(
                        instrumentId,
                        other.instrumentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    watchlistId,
                    instrumentId);
        }
    }
}