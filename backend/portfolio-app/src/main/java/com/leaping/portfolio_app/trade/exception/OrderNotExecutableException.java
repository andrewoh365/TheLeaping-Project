package com.leaping.portfolio_app.trade.exception;

/**
 * Thrown when execution is asked for an order that is no longer awaiting it
 * (already filled, rejected or cancelled). Nothing is changed.
 */
public class OrderNotExecutableException extends IllegalArgumentException {
    public OrderNotExecutableException(String message) {
        super(message);
    }
}
