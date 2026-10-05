package com.leaping.portfolio_app.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private TradeService tradeService;

    private Portfolio portfolio;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio();
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
        when(holdingRepository.findByPortfolioAndInstrument(portfolio, instrument)).thenReturn(Optional.of(existing));
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

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> tradeService.executeTrade(77L)
        );

        assertTrue(ex.getMessage().contains("no current price"));
        assertEquals(OrderStatus.REJECTED, order.getOrderStatus());
        assertTrue(order.getRejectionReason().contains("No valid price"));
        assertNotNull(order.getRejectedAt());
    }
}
