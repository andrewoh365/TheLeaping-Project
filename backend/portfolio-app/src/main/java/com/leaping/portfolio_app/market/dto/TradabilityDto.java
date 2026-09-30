package com.leaping.portfolio_app.market.dto;

import com.leaping.portfolio_app.market.enums.TradabilityReason;

public class TradabilityDto {

    private final String symbol;
    private final boolean tradable;
    private final TradabilityReason reason;
    private final boolean marketOpen;
    private final boolean alwaysOpen;
    private final boolean priceAvailable;
    private final boolean priceStale;
    private final String message;

    public TradabilityDto(
            String symbol,
            boolean tradable,
            TradabilityReason reason,
            boolean marketOpen,
            boolean alwaysOpen,
            boolean priceAvailable,
            boolean priceStale,
            String message
    ) {
        this.symbol = symbol;
        this.tradable = tradable;
        this.reason = reason;
        this.marketOpen = marketOpen;
        this.alwaysOpen = alwaysOpen;
        this.priceAvailable = priceAvailable;
        this.priceStale = priceStale;
        this.message = message;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isTradable() {
        return tradable;
    }

    public TradabilityReason getReason() {
        return reason;
    }

    public boolean isMarketOpen() {
        return marketOpen;
    }

    public boolean isAlwaysOpen() {
        return alwaysOpen;
    }

    public boolean isPriceAvailable() {
        return priceAvailable;
    }

    public boolean isPriceStale() {
        return priceStale;
    }

    public String getMessage() {
        return message;
    }
}
