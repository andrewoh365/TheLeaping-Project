package com.leaping.portfolio_app.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.leaping.portfolio_app.instrument.enums.InstrumentType;

/**
 * Two-sided quote derived from a mid price (BR-13, BR-08).
 *
 * The market feed publishes one price per instrument, treated as the mid. Bid and ask
 * sit half a spread either side of it. Buyers pay the ask and sellers receive the bid,
 * so the preview, the pre-trade checks and execution all price an order the same way.
 *
 * Spreads are a fixed fraction of the mid by asset class, roughly what liquid
 * instruments trade at: large-cap stocks ~4 bps, major FX ~2 bps (1-2 pips), crypto ~20 bps.
 */
public record BidAskQuote(BigDecimal bid, BigDecimal mid, BigDecimal ask) {

    private static final int PRICE_SCALE = 8;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private static final BigDecimal STOCK_SPREAD = new BigDecimal("0.0004");
    private static final BigDecimal FOREX_SPREAD = new BigDecimal("0.0002");
    private static final BigDecimal CRYPTO_SPREAD = new BigDecimal("0.0020");

    /** @return the quote, or null if there is no usable mid price */
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

    /** The side an order trades at: the ask when buying, the bid when selling. */
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
