package com.leaping.portfolio_app.trade.service;

/**
 * Published when an order is accepted. Listeners run after the acceptance has
 * committed, so execution only ever starts on an order already in the permanent record.
 */
public record OrderAcceptedEvent(Long orderId) {
}
