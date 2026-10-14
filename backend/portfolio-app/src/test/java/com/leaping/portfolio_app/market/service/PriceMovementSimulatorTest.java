package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceMovementSimulatorTest {

    private static final OffsetDateTime TICK = OffsetDateTime.of(2026, 10, 7, 14, 0, 0, 0, ZoneOffset.UTC);
    private static final long TICK_MILLIS = 30_000L;
    private static final BigDecimal FLOOR = new BigDecimal("0.0001");

    @Test
    void sameInputsReturnTheSamePrice() {
        PriceMovementSimulator first = new PriceMovementSimulator(0.05d, 0.02d);
        PriceMovementSimulator second = new PriceMovementSimulator(0.05d, 0.02d);

        BigDecimal left = first.nextPrice(
                "AAPL", InstrumentType.STOCK, new BigDecimal("180.25"), new BigDecimal("175.00"),
                250, TICK, TICK_MILLIS);
        BigDecimal right = second.nextPrice(
                "AAPL", InstrumentType.STOCK, new BigDecimal("180.25"), new BigDecimal("175.00"),
                250, TICK, TICK_MILLIS);

        assertEquals(0, left.compareTo(right));
    }

    @Test
    void priceStaysAboveTheFloor() {
        PriceMovementSimulator simulator = new PriceMovementSimulator(0d, 1d);
        BigDecimal last = new BigDecimal("0.0002");

        for (int step = 0; step < 40; step++) {
            BigDecimal next = simulator.nextPrice(
                    "DOGE-USD",
                    InstrumentType.CRYPTO,
                    last,
                    new BigDecimal("0.0001"),
                    50_000,
                    TICK.plusSeconds(step * 30L),
                    TICK_MILLIS);
            assertTrue(next.compareTo(FLOOR) >= 0, "price fell through the floor: " + next);
            last = next;
        }
    }

    @Test
    void stepStaysInsideTheClamp() {
        PriceMovementSimulator simulator = new PriceMovementSimulator(0d, 0.02d);
        BigDecimal last = new BigDecimal("100");
        BigDecimal limit = last.multiply(new BigDecimal("0.02")).add(new BigDecimal("0.0000001"));
        boolean moved = false;

        for (int step = 0; step < 24; step++) {
            BigDecimal next = simulator.nextPrice(
                    "NVDA",
                    InstrumentType.STOCK,
                    last,
                    last,
                    1_000_000,
                    TICK.plusSeconds(step * 30L),
                    86_400_000L);
            BigDecimal delta = next.subtract(last).abs();
            assertTrue(delta.compareTo(limit) <= 0, "step " + delta + " exceeded the clamp");
            moved = moved || delta.signum() > 0;
        }

        assertTrue(moved, "expected at least one tick to move");
    }

    @Test
    void cryptoMovesMoreThanForexForTheSameShock() {
        PriceMovementSimulator simulator = new PriceMovementSimulator(0d, 0.02d);
        BigDecimal anchor = new BigDecimal("100");
        boolean foundWiderCryptoMove = false;

        for (String symbol : new String[] {"AAPL", "MSFT", "BTC-USD", "EUR/USD", "RELIANCE", "XOM"}) {
            BigDecimal crypto = simulator.nextPrice(
                    symbol, InstrumentType.CRYPTO, anchor, anchor, 500, TICK, TICK_MILLIS);
            BigDecimal forex = simulator.nextPrice(
                    symbol, InstrumentType.FOREX, anchor, anchor, 500, TICK, TICK_MILLIS);
            BigDecimal cryptoMove = crypto.subtract(anchor).abs();
            BigDecimal forexMove = forex.subtract(anchor).abs();
            if (cryptoMove.signum() > 0 && cryptoMove.compareTo(forexMove) > 0) {
                foundWiderCryptoMove = true;
                break;
            }
        }

        assertTrue(foundWiderCryptoMove, "expected a crypto step wider than the forex step");
    }

    @Test
    void secondStepDoesNotJumpBackToTheAnchor() {
        PriceMovementSimulator simulator = new PriceMovementSimulator(0.05d, 0.02d);
        BigDecimal anchor = new BigDecimal("100");
        BigDecimal last = new BigDecimal("80");

        BigDecimal first = simulator.nextPrice(
                "AAPL", InstrumentType.STOCK, last, anchor, 300, TICK, TICK_MILLIS);
        BigDecimal second = simulator.nextPrice(
                "AAPL", InstrumentType.STOCK, first, anchor, 300, TICK.plusSeconds(30), TICK_MILLIS);

        BigDecimal firstLimit = last.multiply(new BigDecimal("0.021"));
        assertTrue(first.subtract(last).abs().compareTo(firstLimit) <= 0);
        assertTrue(second.subtract(anchor).abs().compareTo(new BigDecimal("10")) > 0);
        assertTrue(second.subtract(first).abs().compareTo(first.subtract(anchor).abs()) < 0);
    }
}
