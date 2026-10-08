package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Steps a price forward from the last stored print.
 *
 * <p>The shock is a seeded draw for the symbol and tick bucket, so the same
 * inputs always return the same price. A fraction of the gap to the CSV anchor
 * is pulled back on each step, and the move is clamped so one tick cannot
 * jump the price back to that anchor.
 */
@Component
public class PriceMovementSimulator {

    public static final String PROVIDER = "price-simulator";

    private static final BigDecimal PRICE_FLOOR = new BigDecimal("0.0001");
    private static final int PRICE_SCALE = 8;
    private static final double EQUITY_TRADING_DAY_SECONDS = 6.5d * 3600d;
    private static final double ALWAYS_OPEN_DAY_SECONDS = 24d * 3600d;

    private final double meanReversion;
    private final double maxStepFraction;

    public PriceMovementSimulator(
            @Value("${market.simulation.mean-reversion:0.05}") double meanReversion,
            @Value("${market.simulation.max-step-fraction:0.02}") double maxStepFraction
    ) {
        if (meanReversion < 0d || meanReversion > 1d) {
            throw new IllegalArgumentException("mean reversion must be between 0 and 1");
        }
        if (maxStepFraction <= 0d || maxStepFraction > 1d) {
            throw new IllegalArgumentException("max step fraction must be greater than 0 and at most 1");
        }
        this.meanReversion = meanReversion;
        this.maxStepFraction = maxStepFraction;
    }

    public BigDecimal nextPrice(
            String symbol,
            InstrumentType instrumentType,
            BigDecimal lastPrice,
            BigDecimal anchorPrice,
            int dailyVolatilityBps,
            OffsetDateTime tickTimestamp,
            long tickMillis
    ) {
        BigDecimal anchor = positiveOrFloor(anchorPrice);
        BigDecimal last = lastPrice == null || lastPrice.signum() <= 0 ? anchor : lastPrice;

        double tickSeconds = Math.max(1d, tickMillis / 1000d);
        double tradingDaySeconds = instrumentType == InstrumentType.STOCK
                ? EQUITY_TRADING_DAY_SECONDS
                : ALWAYS_OPEN_DAY_SECONDS;
        double dailyVol = Math.max(0, dailyVolatilityBps) / 10_000d;
        double sigma = dailyVol * volatilityClassFactor(instrumentType) * Math.sqrt(tickSeconds / tradingDaySeconds);

        long bucket = tickTimestamp.toEpochSecond() / (long) tickSeconds;
        double shockFraction = gaussian(symbol, bucket) * sigma;
        double reversionFraction = meanReversion * (anchor.doubleValue() - last.doubleValue()) / last.doubleValue();
        double stepFraction = clamp(shockFraction + reversionFraction, -maxStepFraction, maxStepFraction);

        BigDecimal next = last.multiply(BigDecimal.valueOf(1d + stepFraction))
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        if (next.compareTo(PRICE_FLOOR) < 0) {
            return PRICE_FLOOR;
        }
        return next;
    }

    private static BigDecimal positiveOrFloor(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            return PRICE_FLOOR;
        }
        return price;
    }

    private static double volatilityClassFactor(InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.CRYPTO) {
            return 1.25d;
        }
        if (instrumentType == InstrumentType.FOREX) {
            return 0.35d;
        }
        return 0.55d;
    }

    private static double gaussian(String symbol, long bucket) {
        String normalized = symbol == null ? "" : symbol.toUpperCase(Locale.ROOT);
        long symbolSeed = Math.abs((long) normalized.hashCode());
        long seed = bucket * 1_103_515_245L + symbolSeed * 12_345L;
        double u1 = Math.max(uniform(seed), 1e-12d);
        double u2 = uniform(seed * 1_664_525L + 1_013_904_223L);
        double z = Math.sqrt(-2d * Math.log(u1)) * Math.cos(2d * Math.PI * u2);
        return clamp(z, -3d, 3d);
    }

    private static double uniform(long seed) {
        return (seed & 0x7fffffffL) / (double) Integer.MAX_VALUE;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
