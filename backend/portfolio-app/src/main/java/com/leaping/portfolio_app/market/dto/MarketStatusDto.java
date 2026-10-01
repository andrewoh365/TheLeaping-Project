package com.leaping.portfolio_app.market.dto;

import java.time.OffsetDateTime;

public class MarketStatusDto {

    private String marketName;
    private String timezone;
    private OffsetDateTime marketTime;
    private boolean open;
    private boolean alwaysOpen;
    private OffsetDateTime nextOpen;
    private OffsetDateTime nextClose;

    public MarketStatusDto(
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
