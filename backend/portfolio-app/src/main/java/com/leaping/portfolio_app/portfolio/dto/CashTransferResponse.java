package com.leaping.portfolio_app.portfolio.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * The recorded cash movement. amountUsd is signed: positive for deposits, negative for withdrawals.
 */
public record CashTransferResponse(
        Long transactionId,
        String transactionType,
        BigDecimal amountUsd,
        BigDecimal balanceBeforeUsd,
        BigDecimal balanceAfterUsd,
        OffsetDateTime createdAt
) {
}
