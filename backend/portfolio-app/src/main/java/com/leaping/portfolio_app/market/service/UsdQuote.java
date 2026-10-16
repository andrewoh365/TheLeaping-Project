package com.leaping.portfolio_app.market.service;

import java.math.BigDecimal;

/**
 * One stored print converted with the fixed currency-table rate.
 * The native price stays in the prices table. Traders are shown usdPrice.
 */
public record UsdQuote(
        BigDecimal nativePrice,
        String currencyCode,
        BigDecimal rateToUsd,
        BigDecimal usdPrice
) {
}
