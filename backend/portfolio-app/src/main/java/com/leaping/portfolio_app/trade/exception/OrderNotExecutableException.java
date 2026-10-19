package com.leaping.portfolio_app.trade.exception;

/** Indicates that an accepted order is not ready for execution yet. */
public class OrderNotExecutableException extends IllegalArgumentException {
    public OrderNotExecutableException(String message) {
        super(message);
    }
}
