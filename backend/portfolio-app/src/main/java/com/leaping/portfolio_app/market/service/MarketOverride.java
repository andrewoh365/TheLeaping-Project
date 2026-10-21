package com.leaping.portfolio_app.market.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Testing switch that treats every market as open, so trades can be tried outside trading hours.
 *
 * It only works where trading.market-override.allowed is true (local development). Everywhere
 * else it can't be turned on. It applies to the whole running app, not one user, and resets
 * to off when the app restarts.
 */
@Component
public class MarketOverride {

    private final boolean allowed;
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    public MarketOverride(@Value("${trading.market-override.allowed:false}") boolean allowed) {
        this.allowed = allowed;
    }

    /** An override that can never be switched on. */
    public static MarketOverride disabled() {
        return new MarketOverride(false);
    }

    public boolean isAllowed() {
        return allowed;
    }

    /** True when markets should be treated as open right now. */
    public boolean isActive() {
        return allowed && enabled.get();
    }

    /**
     * @throws IllegalStateException if this environment doesn't allow the override
     */
    public void setEnabled(boolean value) {
        if (!allowed) {
            throw new IllegalStateException("The market override isn't available in this environment.");
        }
        enabled.set(value);
    }
}
