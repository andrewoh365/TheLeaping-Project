package com.leaping.portfolio_app.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.service.MarketPriceService;
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
import com.leaping.portfolio_app.trade.enums.OrderAction;
import com.leaping.portfolio_app.trade.enums.OrderStatus;
import com.leaping.portfolio_app.trade.enums.OrderType;
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
    }

    @Test
    void executeTradeBuyShouldUpdateHoldingAndCashAndFillOrder() {
        TradeOrder order = new TradeOrder();
        order.setOrderId(10L);
        order.setPortfolio(portfolio);
        order.setInstrument(instrument);
        order.setOrderAction(OrderAction.BUY);
        order.setOrderType(OrderType.MARKET);
        order.setOrderStatus(OrderStatus.SUBMITTED);
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
        assertEquals(new BigDecimal("200.00"), response.getTotalUsdValue());

        assertEquals(new BigDecimal("7"), existing.getQuantity());
        assertEquals(new BigDecimal("92.85714286"), existing.getAverageCostUsd());
        assertEquals(new BigDecimal("800.00"), portfolio.getCashBalanceUsd());
        assertEquals(OrderStatus.FILLED, order.getOrderStatus());
        assertNotNull(order.getFilledAt());

        // Order and portfolio must be re-read from the DB under a row lock, not taken from cache.
        verify(entityManager).refresh(order, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

        ArgumentCaptor<CashTransaction> cashCaptor = ArgumentCaptor.forClass(CashTransaction.class);
        verify(cashTransactionRepository).save(cashCaptor.capture());
        assertEquals(CashTransactionType.TRADE_BUY, cashCaptor.getValue().getTransactionType());
        assertEquals(new BigDecimal("-200.00"), cashCaptor.getValue().getAmountUsd());
    }

    @Test
    void executeTradeShouldRejectOrderWhenPriceUnavailable() {
        TradeOrder order = new TradeOrder();
        order.setOrderId(77L);
        order.setPortfolio(portfolio);
        order.setInstrument(instrument);
        order.setOrderAction(OrderAction.SELL);
        order.setOrderType(OrderType.MARKET);
        order.setOrderStatus(OrderStatus.SUBMITTED);
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

        assertEquals("Not enough cash. This order costs $200.00 and you have $150.00 available.", ex.getMessage());
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
    void executeTradeShouldRefuseOrderThatIsNotSubmitted() {
        TradeOrder order = marketOrder(13L, OrderAction.BUY, "1");
        order.setOrderStatus(OrderStatus.FILLED);

        when(tradeOrderRepository.findById(13L)).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> tradeService.executeTrade(13L));
        verify(entityManager, never()).refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(tradeRepository, cashTransactionRepository);
    }

    private TradeOrder marketOrder(Long orderId, OrderAction action, String quantity) {
        TradeOrder order = new TradeOrder();
        order.setOrderId(orderId);
        order.setPortfolio(portfolio);
        order.setInstrument(instrument);
        order.setOrderAction(action);
        order.setOrderType(OrderType.MARKET);
        order.setOrderStatus(OrderStatus.SUBMITTED);
        order.setQuantity(new BigDecimal(quantity));
        return order;
    }
}
