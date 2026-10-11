package com.leaping.portfolio_app.trade.exception;

/**
 * Thrown when an order is rejected at execution time (no price, insufficient
 * cash or holding). The order has already been marked REJECTED when this is
 * thrown, and TradeService is configured not to roll back on it so that the
 * rejection is persisted as part of the order's permanent record.
 *
 * Extends IllegalArgumentException so existing callers that treat execution
 * failures as bad requests keep working.
 */
public class TradeRejectedException extends IllegalArgumentException {
    public TradeRejectedException(String message) {
        super(message);
    }
}
