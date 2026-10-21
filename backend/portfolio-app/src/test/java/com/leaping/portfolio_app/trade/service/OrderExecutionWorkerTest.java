package com.leaping.portfolio_app.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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

import com.leaping.portfolio_app.trade.exception.TradeRejectedException;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderExecutionWorkerTest {

    @Mock
    private TradeService tradeService;

    @Mock
    private TradeOrderService tradeOrderService;

    @Mock
    private TradeOrderRepository tradeOrderRepository;

    @InjectMocks
    private OrderExecutionWorker worker;

    @Test
    void executionStartsOnlyAfterAcceptanceCommits() throws NoSuchMethodException {
        TransactionalEventListener listener = OrderExecutionWorker.class
                .getMethod("onOrderAccepted", OrderAcceptedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertNotNull(listener);
        assertEquals(TransactionPhase.AFTER_COMMIT, listener.phase());
    }

    @Test
    void acceptedOrderIsSentToExecution() {
        worker.onOrderAccepted(new OrderAcceptedEvent(1L));

        verify(tradeService).executeTrade(1L);
    }

    @Test
    void businessRejectionDoesNotBecomeTechnicalRetry() {
        when(tradeService.executeTrade(2L))
                .thenThrow(new TradeRejectedException("Not enough cash."));

        worker.execute(2L);

        verify(tradeService).executeTrade(2L);
    }

    @Test
    void stalledAcceptedMarketOrdersAreRetried() {
        when(tradeOrderRepository.findAcceptedDayOrderIds()).thenReturn(List.of());
        when(tradeOrderRepository.findOrdersAwaitingExecution(any(), any()))
                .thenReturn(List.of(3L, 4L));

        worker.retryAwaitingOrders();

        verify(tradeService).executeTrade(3L);
        verify(tradeService).executeTrade(4L);
    }

    @Test
    void executionFailureDoesNotChangeAcceptedOrderInWorker() {
        when(tradeService.executeTrade(5L))
                .thenThrow(new IllegalStateException("Connection reset"));

        worker.execute(5L);

        verify(tradeService).executeTrade(5L);
    }
}
