package com.leaping.portfolio_app.market.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.leaping.portfolio_app.market.entity.Market;

class MarketOverrideTest {

    /** A market whose trading window starts two hours from now, so it is closed at the moment. */
    private static Market closedMarket() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Market market = new Market();
        market.setMarketName("TEST");
        market.setTimezone("UTC");
        market.setOpenTime(now.plusHours(2).toLocalTime());
        market.setCloseTime(now.plusHours(3).toLocalTime());
        return market;
    }

    @Test
    void marketIsClosedOutsideItsHoursWithoutTheOverride() {
        MarketOverride override = new MarketOverride(true);

        assertFalse(new MarketHoursService(override).getMarketStatus(closedMarket()).isOpen());
    }

    @Test
    void overrideHoldsAClosedMarketOpenUntilTurnedOff() {
        MarketOverride override = new MarketOverride(true);
        MarketHoursService hours = new MarketHoursService(override);

        override.setEnabled(true);
        assertTrue(hours.getMarketStatus(closedMarket()).isOpen());

        override.setEnabled(false);
        assertFalse(hours.getMarketStatus(closedMarket()).isOpen());
    }

    @Test
    void overrideCannotBeTurnedOnWhereItIsNotAllowed() {
        MarketOverride override = new MarketOverride(false);

        assertThrows(IllegalStateException.class, () -> override.setEnabled(true));
        assertFalse(override.isActive());
        assertFalse(new MarketHoursService(override).getMarketStatus(closedMarket()).isOpen());
    }
}
