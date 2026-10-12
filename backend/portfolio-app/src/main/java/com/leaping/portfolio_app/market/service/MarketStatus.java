package com.leaping.portfolio_app.market.service;

import java.time.OffsetDateTime;

public class MarketStatus {

    private final String marketName;
    private final String timezone;
    private final OffsetDateTime marketTime;
    private final boolean open;
    private final boolean alwaysOpen;
    private final OffsetDateTime nextOpen;
    private final OffsetDateTime nextClose;

    public MarketStatus(
            String marketName,
            String timezone,
            OffsetDateTime marketTime,
            boolean open,
            boolean alwaysOpen,
            OffsetDateTime nextOpen,
            OffsetDateTime nextClose
    ) {
        this.marketName = marketName;
        this.timezone = timezone;
        this.marketTime = marketTime;
        this.open = open;
        this.alwaysOpen = alwaysOpen;
        this.nextOpen = nextOpen;
        this.nextClose = nextClose;
    }

    public String getMarketName() {
        return marketName;
    }

    public String getTimezone() {
        return timezone;
    }

    public OffsetDateTime getMarketTime() {
        return marketTime;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isAlwaysOpen() {
        return alwaysOpen;
    }

    public OffsetDateTime getNextOpen() {
        return nextOpen;
    }

    public OffsetDateTime getNextClose() {
        return nextClose;
    }
}
