package com.leaping.portfolio_app.market.dto;

import java.time.OffsetDateTime;

/**
 * A market news headline from an external feed. Only plain text and an http(s) link are kept.
 */
public record NewsItemDto(
        String title,
        String source,
        String url,
        OffsetDateTime publishedAt
) {
}
