
package com.leaping.portfolio_app.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateWatchlistRequest {

    @NotBlank(message = "Watchlist name is required")
    @Size(max = 100, message = "Watchlist name must be 100 characters or less")
    private String watchlistName;

    public CreateWatchlistRequest() {
    }

    public CreateWatchlistRequest(String watchlistName) {
        this.watchlistName = watchlistName;
    }

    public String getWatchlistName() {
        return watchlistName;
    }

    public void setWatchlistName(String watchlistName) {
        this.watchlistName = watchlistName;
    }
}
