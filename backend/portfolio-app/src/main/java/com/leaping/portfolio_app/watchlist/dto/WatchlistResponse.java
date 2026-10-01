
package com.leaping.portfolio_app.watchlist.dto;

import java.time.OffsetDateTime;

public class WatchlistResponse {

    private Long watchlistId;

    private String watchlistName;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public WatchlistResponse() {
    }

    public WatchlistResponse(
            Long watchlistId,
            String watchlistName,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.watchlistId = watchlistId;
        this.watchlistName = watchlistName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getWatchlistId() {
        return watchlistId;
    }

    public void setWatchlistId(Long watchlistId) {
        this.watchlistId = watchlistId;
    }

    public String getWatchlistName() {
        return watchlistName;
    }

    public void setWatchlistName(String watchlistName) {
        this.watchlistName = watchlistName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
