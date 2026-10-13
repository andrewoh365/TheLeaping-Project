package com.leaping.portfolio_app.portfolio.dto;

import java.math.BigDecimal;

/**
 * Add cash to, or take cash out of, the client's balance.
 * There is no bank integration in this phase (BRS 4.2): cash movements are modelled within the platform.
 *
 * @param direction "DEPOSIT" or "WITHDRAW"
 * @param amount    positive USD amount, at most 2 decimal places
 */
public record CashTransferRequest(String direction, BigDecimal amount) {
}
