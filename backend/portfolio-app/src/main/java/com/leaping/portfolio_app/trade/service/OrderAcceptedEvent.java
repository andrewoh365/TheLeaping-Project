/**
 * Published after an order has been accepted and its transaction commits.
 */
package com.leaping.portfolio_app.trade.service;

public record OrderAcceptedEvent(Long orderId) {
}
