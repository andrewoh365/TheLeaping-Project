package com.leaping.portfolio_app.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.leaping.portfolio_app.trade.exception.OrderNotExecutableException;
import com.leaping.portfolio_app.trade.exception.TradeRejectedException;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderExecutionWorkerTest {

    @Mock
    private TradeService tradeService;

    @Mock
    private TradeOrderRepository tradeOrderRepository;

    @InjectMocks
    private OrderExecutionWorker worker;

    @Test
    void executionShouldOnlyStartAfterAcceptanceCommits() throws NoSuchMethodException {
        TransactionalEventListener listener = OrderExecutionWorker.class
            .getMethod("onOrderAccepted", OrderAcceptedEvent.class)
            .getAnnotation(TransactionalEventListener.class);

        assertNotNull(listener);
        assertEquals(TransactionPhase.AFTER_COMMIT, listener.phase());
    }

    @Test
    void acceptedOrderShouldBeExecuted() {
        worker.onOrderAccepted(new OrderAcceptedEvent(1L));

        verify(tradeService).executeTrade(1L);
        verify(tradeService, never()).recordFailedAttempt(anyLong(), anyString(), anyInt());
    }

    @Test
    void rejectionShouldNotCountAsFailedAttempt() {
        when(tradeService.executeTrade(2L)).thenThrow(new TradeRejectedException("Not enough cash."));

        worker.execute(2L);

        verify(tradeService, never()).recordFailedAttempt(anyLong(), anyString(), anyInt());
    }

    @Test
    void orderAlreadyHandledShouldBeSkipped() {
        when(tradeService.executeTrade(3L)).thenThrow(new OrderNotExecutableException("Order cannot be executed in FILLED status"));

        worker.execute(3L);

        verify(tradeService, never()).recordFailedAttempt(anyLong(), anyString(), anyInt());
    }

    @Test
    void technicalFailureShouldBeRecordedSoOrderIsRetried() {
        when(tradeService.executeTrade(4L)).thenThrow(new IllegalStateException("Connection reset"));

        worker.execute(4L);

        verify(tradeService).recordFailedAttempt(4L, "Connection reset", OrderExecutionWorker.MAX_ATTEMPTS);
    }

    @Test
    void sweepShouldRetryStalledAcceptedOrders() {
        when(tradeOrderRepository.findStalledMarketOrderIds(any(), any())).thenReturn(List.of(5L, 6L));

        worker.retryStalledOrders();

        verify(tradeService).executeTrade(5L);
        verify(tradeService).executeTrade(6L);
    }
}
