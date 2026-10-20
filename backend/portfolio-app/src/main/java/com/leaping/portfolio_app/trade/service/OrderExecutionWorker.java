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

import com.leaping.portfolio_app.trade.exception.TradeRejectedException;
import com.leaping.portfolio_app.trade.exception.OrderNotExecutableException;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;

/** Executes accepted market orders after acceptance has committed. */
@Component
public class OrderExecutionWorker {
    private static final Logger log = LoggerFactory.getLogger(OrderExecutionWorker.class);
    private static final Duration RETRY_AFTER = Duration.ofSeconds(10);
    private static final Duration RETRY_WINDOW = Duration.ofMinutes(15);

    private final TradeService tradeService;
    private final TradeOrderService tradeOrderService;
    private final TradeOrderRepository tradeOrderRepository;

    public OrderExecutionWorker(
            TradeService tradeService,
            TradeOrderService tradeOrderService,
            TradeOrderRepository tradeOrderRepository
    ) {
        this.tradeService = tradeService;
        this.tradeOrderService = tradeOrderService;
        this.tradeOrderRepository = tradeOrderRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderAccepted(OrderAcceptedEvent event) {
        execute(event.orderId());
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 15_000)
    public void retryAwaitingOrders() {
        OffsetDateTime now = OffsetDateTime.now();
        for (Long orderId : tradeOrderRepository.findAcceptedDayOrderIds()) {
            tradeOrderService.expireDayOrder(orderId);
        }
        for (Long orderId : tradeOrderRepository.findOrdersAwaitingExecution(
                now.minus(RETRY_WINDOW), now.minus(RETRY_AFTER))) {
            execute(orderId);
        }
    }

    void execute(Long orderId) {
        try {
            tradeService.executeTrade(orderId);
        } catch (OrderNotExecutableException exception) {
            log.debug("Order {} is not executable yet: {}", orderId, exception.getMessage());
        } catch (TradeRejectedException exception) {
            log.info("Order {} rejected at execution: {}", orderId, exception.getMessage());
        } catch (RuntimeException exception) {
            // The execution transaction rolls back and leaves the order ACCEPTED.
            // The scheduled sweep will retry it while it remains inside the retry window.
            log.error("Execution failed for order {}; it remains accepted for retry", orderId, exception);
        }
    }
}
