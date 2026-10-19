package com.leaping.portfolio_app.trade.enums;

/**
 * Order lifecycle, as in a broker's order management system:
 *
 * <pre>
 * SUBMITTED ──► ACCEPTED ──► FILLED
 *     │             │
 *     │             ├──► REJECTED   (could not be executed, e.g. no price)
 *     │             └──► CANCELLED  (client cancelled before it filled)
 *     └──► REJECTED                 (failed pre-trade checks, BR-05)
 * </pre>
 *
 * SUBMITTED: received and recorded, not yet checked.
 * ACCEPTED: passed pre-trade checks; a firm commitment awaiting execution.
 * FILLED, REJECTED, CANCELLED: final outcomes; an order never leaves them.
 */
public enum OrderStatus {
    SUBMITTED,
    ACCEPTED,
    FILLED,
    REJECTED,
    CANCELLED;

    /** Open orders are still working: not yet filled, rejected or cancelled. */
    public boolean isOpen() {
        return this == SUBMITTED || this == ACCEPTED;
    }

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case SUBMITTED -> next == ACCEPTED || next == REJECTED || next == CANCELLED;
            case ACCEPTED -> next == FILLED || next == REJECTED || next == CANCELLED;
            case FILLED, REJECTED, CANCELLED -> false;
        };
    }
}
