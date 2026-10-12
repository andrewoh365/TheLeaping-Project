package com.leaping.portfolio_app.market.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class QuoteDto {

    private final String symbol;
    private final BigDecimal price;
    private final String currencyCode;
    private final OffsetDateTime timestamp;
    private final String sourceType;
    private final String provider;
    private final boolean stale;
    private final long ageSeconds;

    public QuoteDto(
            String symbol,
            BigDecimal price,
            String currencyCode,
            OffsetDateTime timestamp,
            String sourceType,
            String provider,
            boolean stale,
            long ageSeconds
    ) {
        this.symbol = symbol;
        this.price = price;
        this.currencyCode = currencyCode;
        this.timestamp = timestamp;
        this.sourceType = sourceType;
        this.provider = provider;
        this.stale = stale;
        this.ageSeconds = ageSeconds;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getProvider() {
        return provider;
    }

    public boolean isStale() {
        return stale;
    }

    public long getAgeSeconds() {
        return ageSeconds;
    }
}
