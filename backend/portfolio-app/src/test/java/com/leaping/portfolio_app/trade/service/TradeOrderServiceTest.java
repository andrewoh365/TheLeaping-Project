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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.service.MarketPriceService;
import com.leaping.portfolio_app.holdings.Holding;
import com.leaping.portfolio_app.holdings.HoldingRepository;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import com.leaping.portfolio_app.trade.dto.OrderResponse;
import com.leaping.portfolio_app.trade.dto.PlaceOrderRequest;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.enums.OrderAction;
import com.leaping.portfolio_app.trade.enums.OrderStatus;
import com.leaping.portfolio_app.trade.enums.OrderType;
import com.leaping.portfolio_app.trade.enums.TimeInForce;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;

@ExtendWith(MockitoExtension.class)
class TradeOrderServiceTest {

    @Mock
    private TradeOrderRepository tradeOrderRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private MarketPriceService marketPriceService;

    @InjectMocks
    private TradeOrderService tradeOrderService;

    private Portfolio portfolio;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio();
        portfolio.setPortfolioId(1L);
        portfolio.setCashBalanceUsd(new BigDecimal("10000.00"));

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
    void placeOrderBuyMarketShouldPersistWhenCashIsSufficient() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setInstrumentId(100L);
        request.setOrderAction("BUY");
        request.setOrderType("MARKET");
        request.setQuantity(new BigDecimal("10"));
        request.setTimeInForce("DAY");

        when(portfolioRepository.findByCustomer_UserId(1L)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(100L)).thenReturn(Optional.of(instrument));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> {
            TradeOrder order = invocation.getArgument(0);
            order.setOrderId(900L);
            return order;
        });

        TradeOrder saved = tradeOrderService.placeOrder(1L, request);

        assertNotNull(saved);
        assertEquals(900L, saved.getOrderId());
        assertEquals(OrderAction.BUY, saved.getOrderAction());
        assertEquals(OrderType.MARKET, saved.getOrderType());
        assertEquals(OrderStatus.SUBMITTED, saved.getOrderStatus());
        verify(tradeOrderRepository).save(any(TradeOrder.class));
    }

    @Test
    void placeOrderBuyShouldFailWhenCashIsInsufficient() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setInstrumentId(100L);
        request.setOrderAction("BUY");
        request.setOrderType("MARKET");
        request.setQuantity(new BigDecimal("100"));

        when(portfolioRepository.findByCustomer_UserId(1L)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(100L)).thenReturn(Optional.of(instrument));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("200.00"));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> tradeOrderService.placeOrder(1L, request)
        );

        assertTrue(ex.getMessage().contains("Insufficient cash"));
    }

    @Test
    void placeOrderSellShouldFailWhenHoldingDoesNotExist() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setInstrumentId(100L);
        request.setOrderAction("SELL");
        request.setOrderType("MARKET");
        request.setQuantity(new BigDecimal("2"));

        when(portfolioRepository.findByCustomer_UserId(1L)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(100L)).thenReturn(Optional.of(instrument));
        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(1L, 100L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> tradeOrderService.placeOrder(1L, request)
        );

        assertTrue(ex.getMessage().contains("not owned"));
    }

    @Test
    void cancelOrderShouldSetCancelledStatusForSubmittedOrder() {
        TradeOrder order = new TradeOrder();
        order.setOrderId(500L);
        order.setPortfolio(portfolio);
        order.setInstrument(instrument);
        order.setOrderStatus(OrderStatus.SUBMITTED);
        order.setOrderAction(OrderAction.BUY);
        order.setOrderType(OrderType.MARKET);
        order.setTimeInForce(TimeInForce.DAY);
        order.setQuantity(new BigDecimal("1"));

        when(tradeOrderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = tradeOrderService.cancelOrder(500L, 1L);

        assertEquals("CANCELLED", response.getOrderStatus());
        assertEquals("DAY", response.getTimeInForce());
        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        assertNotNull(order.getCancelledAt());
    }
}
