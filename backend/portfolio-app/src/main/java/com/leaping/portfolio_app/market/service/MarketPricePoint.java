package com.leaping.portfolio_app.market.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class MarketPricePoint {

    private final OffsetDateTime timestamp;
    private final BigDecimal price;

    public MarketPricePoint(OffsetDateTime timestamp, BigDecimal price) {
        this.timestamp = timestamp;
        this.price = price;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
