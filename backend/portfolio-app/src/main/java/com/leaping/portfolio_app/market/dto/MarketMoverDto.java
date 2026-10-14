package com.leaping.portfolio_app.market.dto;

import java.math.BigDecimal;

/**
 * A stock's move over the latest US trading session.
 *
 * @param instrumentId the platform's instrument ID if the stock can be traded here, otherwise null
 */
public record MarketMoverDto(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        Long instrumentId
) {
}
