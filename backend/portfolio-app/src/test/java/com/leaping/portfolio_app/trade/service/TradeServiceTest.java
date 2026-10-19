package com.leaping.portfolio_app.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.dto.TradabilityDto;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.enums.TradabilityReason;
import com.leaping.portfolio_app.market.service.MarketPriceService;
import com.leaping.portfolio_app.market.service.PreTradeMarketService;
import com.leaping.portfolio_app.holdings.Holding;
import com.leaping.portfolio_app.holdings.HoldingRepository;
import com.leaping.portfolio_app.portfolio.entity.CashTransaction;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.enums.CashTransactionType;
import com.leaping.portfolio_app.portfolio.repository.CashTransactionRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import com.leaping.portfolio_app.trade.dto.TradeConfirmationResponse;
import com.leaping.portfolio_app.trade.entity.ExecutionAttempt;
import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.enums.ExecutionAttemptStatus;
import com.leaping.portfolio_app.trade.enums.OrderAction;
import com.leaping.portfolio_app.trade.enums.OrderStatus;
import com.leaping.portfolio_app.trade.enums.OrderType;
import com.leaping.portfolio_app.trade.exception.OrderNotExecutableException;
import com.leaping.portfolio_app.trade.exception.TradeRejectedException;
import com.leaping.portfolio_app.trade.repository.ExecutionAttemptRepository;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private TradeOrderRepository tradeOrderRepository;

    @Mock
    private ExecutionAttemptRepository executionAttemptRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private CashTransactionRepository cashTransactionRepository;

    @Mock
    private MarketPriceService marketPriceService;

    @Mock
    private PreTradeMarketService preTradeMarketService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TradeService tradeService;

    private Portfolio portfolio;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio();
        portfolio.setPortfolioId(1L);
        portfolio.setCashBalanceUsd(new BigDecimal("1000.00"));

        Customer customer = new Customer();
        customer.setUserId(1L);
        portfolio.setCustomer(customer);

        Currency usd = new Currency();
        usd.setCurrencyCode("USD");

        instrument = new Instrument();
        instrument.setInstrumentId(100L);
        instrument.setSymbol("AAPL");
        instrument.setPriceCurrency(usd);

        // Market-hours checks at fill time are covered by their own test
        ReflectionTestUtils.setField(tradeService, "enforceTradability", false);
    }

    @Test
    void executeTradeBuyShouldUpdateHoldingAndCashAndFillOrder() {
        TradeOrder order = new TradeOrder();
        order.setOrderId(10L);
        order.setPortfolio(portfolio);
        order.setInstrument(instrument);
        order.setOrderAction(OrderAction.BUY);
        order.setOrderType(OrderType.MARKET);
        order.setOrderStatus(OrderStatus.ACCEPTED);
        order.setQuantity(new BigDecimal("2"));

        Holding existing = new Holding();
        existing.setPortfolio(portfolio);
        existing.setInstrument(instrument);
        existing.setQuantity(new BigDecimal("5"));
        existing.setAverageCostUsd(new BigDecimal("90.00"));

        when(tradeOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));
        when(executionAttemptRepository.save(any(ExecutionAttempt.class))).thenAnswer(invocation -> {
            ExecutionAttempt attempt = invocation.getArgument(0);
            attempt.setExecutionAttemptId(200L);
            return attempt;
        });
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setTradeId(300L);
            return trade;
        });
        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(1L, 100L)).thenReturn(Optional.of(existing));
        when(holdingRepository.save(any(Holding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashTransactionRepository.save(any(CashTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeConfirmationResponse response = tradeService.executeTrade(10L);

        assertNotNull(response);
        assertEquals(300L, response.getTradeId());
        assertEquals(10L, response.getOrderId());
        assertEquals("BUY", response.getOrderAction());
        // A buy fills at the ask: mid 100.00, ask 100.02
        assertEquals(new BigDecimal("100.02000000"), response.getExecutionPrice());
        assertEquals(new BigDecimal("200.04000000"), response.getTotalUsdValue());

        assertEquals(new BigDecimal("7"), existing.getQuantity());
        assertEquals(new BigDecimal("92.86285714"), existing.getAverageCostUsd());
        assertEquals(new BigDecimal("799.96000000"), portfolio.getCashBalanceUsd());
        assertEquals(OrderStatus.FILLED, order.getOrderStatus());
        assertNotNull(order.getFilledAt());

        // Order and portfolio must be re-read from the DB under a row lock, not taken from cache.
        verify(entityManager).refresh(order, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

        ArgumentCaptor<CashTransaction> cashCaptor = ArgumentCaptor.forClass(CashTransaction.class);
        verify(cashTransactionRepository).save(cashCaptor.capture());
        assertEquals(CashTransactionType.TRADE_BUY, cashCaptor.getValue().getTransactionType());
        assertEquals(new BigDecimal("-200.04000000"), cashCaptor.getValue().getAmountUsd());
    }

    @Test
    void executeTradeFullSellShouldDeleteHoldingInsteadOfSavingZeroQuantity() {
        TradeOrder order = marketOrder(14L, OrderAction.SELL, "5");

        Holding existing = new Holding();
        existing.setPortfolio(portfolio);
        existing.setInstrument(instrument);
        existing.setQuantity(new BigDecimal("5"));
        existing.setAverageCostUsd(new BigDecimal("90.00"));

        when(tradeOrderRepository.findById(14L)).thenReturn(Optional.of(order));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));
        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(1L, 100L))
            .thenReturn(Optional.of(existing));
        when(executionAttemptRepository.save(any(ExecutionAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashTransactionRepository.save(any(CashTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeConfirmationResponse response = tradeService.executeTrade(14L);

        // A sell fills at the bid: mid 100.00, bid 99.98
        assertEquals(new BigDecimal("99.98000000"), response.getExecutionPrice());
        assertEquals(new BigDecimal("499.90000000"), response.getTotalUsdValue());
        assertEquals(new BigDecimal("1499.90000000"), portfolio.getCashBalanceUsd());
        assertEquals(OrderStatus.FILLED, order.getOrderStatus());
        verify(holdingRepository).delete(existing);
        verify(holdingRepository, never()).save(any(Holding.class));
    }

    @Test
    void executeTradeShouldRejectOrderWhenPriceUnavailable() {
        TradeOrder order = new TradeOrder();
        order.setOrderId(77L);
        order.setPortfolio(portfolio);
        order.setInstrument(instrument);
        order.setOrderAction(OrderAction.SELL);
        order.setOrderType(OrderType.MARKET);
        order.setOrderStatus(OrderStatus.ACCEPTED);
        order.setQuantity(new BigDecimal("1"));

        when(tradeOrderRepository.findById(77L)).thenReturn(Optional.of(order));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(BigDecimal.ZERO);
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeRejectedException ex = assertThrows(
            TradeRejectedException.class,
            () -> tradeService.executeTrade(77L)
        );

        assertEquals("We don't have a current price for AAPL, so the order couldn't be filled.", ex.getMessage());
        assertEquals(OrderStatus.REJECTED, order.getOrderStatus());
        assertEquals(ex.getMessage(), order.getRejectionReason());
        assertNotNull(order.getRejectedAt());
        verifyNoInteractions(tradeRepository, executionAttemptRepository, cashTransactionRepository);
    }

    @Test
    void executeTradeShouldNotRollBackWhenOrderIsRejected() throws NoSuchMethodException {
        // A rejection must be committed so the REJECTED status survives (BR-06, BR-14).
        Transactional tx = TradeService.class
            .getMethod("executeTrade", Long.class)
            .getAnnotation(Transactional.class);

        assertNotNull(tx);
        assertTrue(Arrays.asList(tx.noRollbackFor()).contains(TradeRejectedException.class));
    }

    @Test
    void executeTradeShouldRejectBuyWhenCashInsufficientAtExecutionPrice() {
        // Cash was enough when the order was placed, but another fill spent it first.
        portfolio.setCashBalanceUsd(new BigDecimal("150.00"));
        TradeOrder order = marketOrder(11L, OrderAction.BUY, "2");

        when(tradeOrderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeRejectedException ex = assertThrows(TradeRejectedException.class, () -> tradeService.executeTrade(11L));

        assertEquals("Not enough cash. This order costs $200.04 and you have $150.00 available.", ex.getMessage());
        assertEquals(OrderStatus.REJECTED, order.getOrderStatus());
        assertEquals(new BigDecimal("150.00"), portfolio.getCashBalanceUsd());
        verifyNoInteractions(tradeRepository, executionAttemptRepository, cashTransactionRepository);
        verify(holdingRepository, never()).save(any(Holding.class));
    }

    @Test
    void executeTradeShouldRejectSellWhenHoldingInsufficient() {
        TradeOrder order = marketOrder(12L, OrderAction.SELL, "10");

        Holding existing = new Holding();
        existing.setPortfolio(portfolio);
        existing.setInstrument(instrument);
        existing.setQuantity(new BigDecimal("3"));

        when(tradeOrderRepository.findById(12L)).thenReturn(Optional.of(order));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));
        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(1L, 100L)).thenReturn(Optional.of(existing));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeRejectedException ex = assertThrows(TradeRejectedException.class, () -> tradeService.executeTrade(12L));

        assertEquals("You only own 3 AAPL.", ex.getMessage());
        assertEquals(OrderStatus.REJECTED, order.getOrderStatus());
        assertEquals(new BigDecimal("3"), existing.getQuantity());
        verifyNoInteractions(tradeRepository, executionAttemptRepository, cashTransactionRepository);
    }

    @Test
    void executeTradeShouldRefuseOrderThatIsNoLongerAccepted() {
        TradeOrder order = marketOrder(13L, OrderAction.BUY, "1");
        order.setOrderStatus(OrderStatus.FILLED);

        when(tradeOrderRepository.findById(13L)).thenReturn(Optional.of(order));

        assertThrows(OrderNotExecutableException.class, () -> tradeService.executeTrade(13L));
        assertEquals(OrderStatus.FILLED, order.getOrderStatus());
        verify(entityManager, never()).refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(tradeRepository, cashTransactionRepository);
    }

    @Test
    void executeTradeShouldRefuseOrderThatWasNeverAccepted() {
        // Execution only ever starts from a recorded acceptance
        TradeOrder order = marketOrder(15L, OrderAction.BUY, "1");
        order.setOrderStatus(OrderStatus.SUBMITTED);

        when(tradeOrderRepository.findById(15L)).thenReturn(Optional.of(order));

        assertThrows(OrderNotExecutableException.class, () -> tradeService.executeTrade(15L));
        assertEquals(OrderStatus.SUBMITTED, order.getOrderStatus());
        verifyNoInteractions(tradeRepository, cashTransactionRepository);
    }

    @Test
    void executeTradeShouldNeverFillLimitOrderAtMarket() {
        TradeOrder order = marketOrder(21L, OrderAction.BUY, "1");
        order.setOrderType(OrderType.LIMIT);
        order.setLimitPriceUsd(new BigDecimal("1.00"));

        when(tradeOrderRepository.findById(21L)).thenReturn(Optional.of(order));

        assertThrows(OrderNotExecutableException.class, () -> tradeService.executeTrade(21L));
        assertEquals(OrderStatus.ACCEPTED, order.getOrderStatus());
        verifyNoInteractions(tradeRepository, executionAttemptRepository, cashTransactionRepository);
    }

    @Test
    void executeTradeShouldRejectWhenMarketClosedAfterAcceptance() {
        ReflectionTestUtils.setField(tradeService, "enforceTradability", true);
        TradeOrder order = marketOrder(16L, OrderAction.BUY, "1");
        OffsetDateTime acceptedAt = OffsetDateTime.now().minusMinutes(1);
        order.setAcceptedAt(acceptedAt);

        when(tradeOrderRepository.findById(16L)).thenReturn(Optional.of(order));
        when(preTradeMarketService.getTradability("AAPL")).thenReturn(new TradabilityDto(
            "AAPL", false, TradabilityReason.MARKET_CLOSED, false, false, false, false, "Market is currently closed"));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeRejectedException ex = assertThrows(TradeRejectedException.class, () -> tradeService.executeTrade(16L));

        assertEquals("The market closed before your order could be filled.", ex.getMessage());
        assertEquals(OrderStatus.REJECTED, order.getOrderStatus());
        // The acceptance itself is untouched by the outcome
        assertEquals(acceptedAt, order.getAcceptedAt());
        assertEquals(new BigDecimal("1"), order.getQuantity());
        verifyNoInteractions(tradeRepository, executionAttemptRepository, cashTransactionRepository);
    }

    @Test
    void executeTradeShouldNumberTheFillAfterEarlierFailedAttempts() {
        TradeOrder order = marketOrder(17L, OrderAction.BUY, "1");

        when(tradeOrderRepository.findById(17L)).thenReturn(Optional.of(order));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));
        when(executionAttemptRepository.countByOrderOrderId(17L)).thenReturn(1L);
        when(executionAttemptRepository.save(any(ExecutionAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(1L, 100L)).thenReturn(Optional.empty());
        when(holdingRepository.save(any(Holding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashTransactionRepository.save(any(CashTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tradeService.executeTrade(17L);

        ArgumentCaptor<ExecutionAttempt> attemptCaptor = ArgumentCaptor.forClass(ExecutionAttempt.class);
        verify(executionAttemptRepository).save(attemptCaptor.capture());
        assertEquals(2, attemptCaptor.getValue().getAttemptNumber());
        assertEquals(OrderStatus.FILLED, order.getOrderStatus());
    }

    @Test
    void recordFailedAttemptShouldKeepOrderAcceptedUntilAttemptsRunOut() {
        TradeOrder order = marketOrder(18L, OrderAction.BUY, "1");

        when(tradeOrderRepository.findById(18L)).thenReturn(Optional.of(order));
        when(executionAttemptRepository.countByOrderOrderId(18L)).thenReturn(0L);

        assertFalse(tradeService.recordFailedAttempt(18L, "Connection reset", 3));

        ArgumentCaptor<ExecutionAttempt> attemptCaptor = ArgumentCaptor.forClass(ExecutionAttempt.class);
        verify(executionAttemptRepository).save(attemptCaptor.capture());
        assertEquals(1, attemptCaptor.getValue().getAttemptNumber());
        assertEquals(ExecutionAttemptStatus.FAILED, attemptCaptor.getValue().getAttemptStatus());
        assertEquals("Connection reset", attemptCaptor.getValue().getReason());
        assertEquals(OrderStatus.ACCEPTED, order.getOrderStatus());
        verify(tradeOrderRepository, never()).save(any(TradeOrder.class));
    }

    @Test
    void recordFailedAttemptShouldRejectOrderOnFinalAttempt() {
        TradeOrder order = marketOrder(19L, OrderAction.BUY, "1");

        when(tradeOrderRepository.findById(19L)).thenReturn(Optional.of(order));
        when(executionAttemptRepository.countByOrderOrderId(19L)).thenReturn(2L);

        assertTrue(tradeService.recordFailedAttempt(19L, "Connection reset", 3));

        assertEquals(OrderStatus.REJECTED, order.getOrderStatus());
        assertEquals(
            "We couldn't complete your order. No money has been taken and your holdings are unchanged.",
            order.getRejectionReason());
        verify(tradeOrderRepository).save(order);
    }

    @Test
    void recordFailedAttemptShouldIgnoreOrderThatIsNoLongerAccepted() {
        TradeOrder order = marketOrder(20L, OrderAction.BUY, "1");
        order.setOrderStatus(OrderStatus.CANCELLED);

        when(tradeOrderRepository.findById(20L)).thenReturn(Optional.of(order));

        assertFalse(tradeService.recordFailedAttempt(20L, "Connection reset", 3));
        verify(executionAttemptRepository, never()).save(any(ExecutionAttempt.class));
        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
    }

    private TradeOrder marketOrder(Long orderId, OrderAction action, String quantity) {
        TradeOrder order = new TradeOrder();
        order.setOrderId(orderId);
        order.setPortfolio(portfolio);
        order.setInstrument(instrument);
        order.setOrderAction(action);
        order.setOrderType(OrderType.MARKET);
        order.setOrderStatus(OrderStatus.ACCEPTED);
        order.setQuantity(new BigDecimal(quantity));
        return order;
    }
}
