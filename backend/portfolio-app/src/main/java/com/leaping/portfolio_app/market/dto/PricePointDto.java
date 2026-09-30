package com.leaping.portfolio_app.market.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class PricePointDto {

    private String symbol;
    private BigDecimal price;
    private String currencyCode;
    private OffsetDateTime timestamp;
    private String sourceType;
    private String provider;

    public PricePointDto(
            String symbol,
            BigDecimal price,
            String currencyCode,
            OffsetDateTime timestamp,
            String sourceType,
            String provider
    ) {
        this.symbol = symbol;
        this.price = price;
        this.currencyCode = currencyCode;
        this.timestamp = timestamp;
        this.sourceType = sourceType;
        this.provider = provider;
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
}
