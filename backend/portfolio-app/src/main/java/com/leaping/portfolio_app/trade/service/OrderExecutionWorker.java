package com.leaping.portfolio_app.trade.service;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.leaping.portfolio_app.trade.exception.OrderNotExecutableException;
import com.leaping.portfolio_app.trade.exception.TradeRejectedException;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;

/**
 * Executes accepted orders asynchronously, after the acceptance is committed.
 *
 * Each accepted order is executed straight away on a background thread. If that attempt
 * fails for a technical reason the order stays ACCEPTED and a sweep retries it, until it
 * fills, is rejected, or runs out of attempts. Execution locks the order row and only
 * acts on ACCEPTED orders, so the immediate run and the sweep never execute an order twice.
 */
@Component
public class OrderExecutionWorker {
    private static final Logger log = LoggerFactory.getLogger(OrderExecutionWorker.class);

    static final int MAX_ATTEMPTS = 3;

    /** Give the immediate execution time to finish before the sweep picks an order up. */
    private static final Duration RETRY_AFTER = Duration.ofSeconds(10);

    /** Market orders older than this are not filled: the price would no longer reflect the client's intent. */
    private static final Duration RETRY_WINDOW = Duration.ofMinutes(15);

    private final TradeService tradeService;
    private final TradeOrderRepository tradeOrderRepository;

    public OrderExecutionWorker(TradeService tradeService, TradeOrderRepository tradeOrderRepository) {
        this.tradeService = tradeService;
        this.tradeOrderRepository = tradeOrderRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderAccepted(OrderAcceptedEvent event) {
        execute(event.orderId());
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 15_000)
    public void retryStalledOrders() {
        OffsetDateTime now = OffsetDateTime.now();
        for (Long orderId : tradeOrderRepository.findStalledMarketOrderIds(now.minus(RETRY_WINDOW), now.minus(RETRY_AFTER))) {
            execute(orderId);
        }
    }

    void execute(Long orderId) {
        try {
            tradeService.executeTrade(orderId);
        } catch (OrderNotExecutableException e) {
            // Already filled, rejected or cancelled by another path
            log.debug("Skipping order {}: {}", orderId, e.getMessage());
        } catch (TradeRejectedException e) {
            log.info("Order {} rejected at execution: {}", orderId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Execution failed for order {}; it stays accepted and will be retried", orderId, e);
            try {
                tradeService.recordFailedAttempt(orderId, e.getMessage(), MAX_ATTEMPTS);
            } catch (RuntimeException recordFailure) {
                log.error("Could not record failed execution attempt for order {}", orderId, recordFailure);
            }
        }
    }
}
