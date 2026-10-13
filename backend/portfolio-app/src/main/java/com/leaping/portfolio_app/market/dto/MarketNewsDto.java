package com.leaping.portfolio_app.market.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Today's market headlines for the dashboard sidebar.
 *
 * @param provider  where the headlines came from
 * @param fetchedAt when the platform last fetched them
 * @param stale     true if the latest fetch failed and an older copy is being served
 */
public record MarketNewsDto(
        List<NewsItemDto> items,
        String provider,
        OffsetDateTime fetchedAt,
        boolean stale
) {
}
