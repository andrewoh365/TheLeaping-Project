package com.leaping.portfolio_app.market.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Biggest gainers and losers among the most actively traded US stocks.
 *
 * @param asOf      the provider's "data as of" label for the session, e.g. "Oct 9, 2026 4:15 PM ET"
 * @param universe  plain-language description of which stocks were ranked
 * @param stale     true if the latest fetch failed and an older copy is being served
 */
public record MarketMoversDto(
        List<MarketMoverDto> gainers,
        List<MarketMoverDto> losers,
        String asOf,
        String universe,
        String provider,
        OffsetDateTime fetchedAt,
        boolean stale
) {
}
