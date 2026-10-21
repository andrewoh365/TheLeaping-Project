package com.leaping.portfolio_app.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.leaping.portfolio_app.instrument.enums.InstrumentType;

/**
 * Two-sided quote derived from a mid price.
 */
public record BidAskQuote(BigDecimal bid, BigDecimal mid, BigDecimal ask) {

    private static final int PRICE_SCALE = 8;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private static final BigDecimal STOCK_SPREAD = new BigDecimal("0.0004");
    private static final BigDecimal FOREX_SPREAD = new BigDecimal("0.0002");
    private static final BigDecimal CRYPTO_SPREAD = new BigDecimal("0.0020");

    public static BidAskQuote fromMid(BigDecimal mid, InstrumentType instrumentType) {
        if (mid == null || mid.signum() <= 0) {
            return null;
        }
        BigDecimal halfSpread = spreadFraction(instrumentType).divide(TWO);
        return new BidAskQuote(
            mid.multiply(BigDecimal.ONE.subtract(halfSpread)).setScale(PRICE_SCALE, RoundingMode.HALF_UP),
            mid,
            mid.multiply(BigDecimal.ONE.add(halfSpread)).setScale(PRICE_SCALE, RoundingMode.HALF_UP)
        );
    }

    public BigDecimal priceFor(boolean buying) {
        return buying ? ask : bid;
    }

    public BigDecimal spread() {
        return ask.subtract(bid);
    }

    static BigDecimal spreadFraction(InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.CRYPTO) {
            return CRYPTO_SPREAD;
        }
        if (instrumentType == InstrumentType.FOREX) {
            return FOREX_SPREAD;
        }
        return STOCK_SPREAD;
    }
}
