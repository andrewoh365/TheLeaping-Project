package com.leaping.portfolio_app.market.dto;

import java.time.LocalTime;

public class MarketDto {

    private String marketName;
    private String country;
    private String timezone;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Boolean active;

    public MarketDto(
            String marketName,
            String country,
            String timezone,
            LocalTime openTime,
            LocalTime closeTime,
            Boolean active
    ) {
        this.marketName = marketName;
        this.country = country;
        this.timezone = timezone;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.active = active;
    }

    public String getMarketName() {
        return marketName;
    }

    public String getCountry() {
        return country;
    }

    public String getTimezone() {
        return timezone;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public Boolean getActive() {
        return active;
    }
}
