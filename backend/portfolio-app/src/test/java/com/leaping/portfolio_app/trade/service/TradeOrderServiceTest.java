package com.leaping.portfolio_app.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import com.leaping.portfolio_app.market.dto.TradabilityDto;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.market.enums.TradabilityReason;
import com.leaping.portfolio_app.market.service.PreTradeMarketService;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.service.MarketPriceService;
import com.leaping.portfolio_app.holdings.Holding;
import com.leaping.portfolio_app.holdings.HoldingRepository;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import com.leaping.portfolio_app.trade.dto.OrderHistoryResponse;
import com.leaping.portfolio_app.trade.dto.OrderPreviewResponse;
import com.leaping.portfolio_app.trade.dto.OrderResponse;
import com.leaping.portfolio_app.trade.dto.PlaceOrderRequest;
import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.enums.OrderAction;
import com.leaping.portfolio_app.trade.enums.OrderStatus;
import com.leaping.portfolio_app.trade.enums.OrderType;
import com.leaping.portfolio_app.trade.enums.TimeInForce;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

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

    @Mock
    private PreTradeMarketService preTradeMarketService;

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private TradeOrderService tradeOrderService;

    private Portfolio portfolio;
    private Instrument instrument;
    private Market market;

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
        instrument.setName("Apple Inc.");
        instrument.setInstrumentType(InstrumentType.STOCK);

        market = new Market();
        market.setMarketName("NASDAQ");
        market.setTimezone("America/New_York");
        market.setOpenTime(LocalTime.of(9, 30));
        market.setCloseTime(LocalTime.of(16, 0));
        instrument.setMarket(market);
    }

    private TradabilityDto tradable() {
        return new TradabilityDto("AAPL", true, TradabilityReason.TRADABLE, true, false, true, false, "Instrument is tradable");
    }

    private TradabilityDto marketClosed() {
        return new TradabilityDto("AAPL", false, TradabilityReason.MARKET_CLOSED, false, false, false, false, "Market is currently closed");
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
        when(preTradeMarketService.getTradability("AAPL")).thenReturn(tradable());
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
        when(preTradeMarketService.getTradability("AAPL")).thenReturn(tradable());

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> tradeOrderService.placeOrder(1L, request)
        );

        assertEquals("Not enough cash. This order costs about $20,000.00 and you have $10,000.00 available.", ex.getMessage());
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
        when(preTradeMarketService.getTradability("AAPL")).thenReturn(tradable());

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> tradeOrderService.placeOrder(1L, request)
        );

        assertEquals("You don't own any AAPL to sell.", ex.getMessage());
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

    @Test
    void placeOrderShouldFailWhenMarketIsClosed() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setInstrumentId(100L);
        request.setOrderAction("BUY");
        request.setOrderType("MARKET");
        request.setQuantity(new BigDecimal("1"));

        when(portfolioRepository.findByCustomer_UserId(1L)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(100L)).thenReturn(Optional.of(instrument));
        when(preTradeMarketService.getTradability("AAPL")).thenReturn(marketClosed());

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> tradeOrderService.placeOrder(1L, request)
        );

        assertEquals("NASDAQ is closed right now. It trades 09:30\u201316:00 (America/New_York).", ex.getMessage());
        verify(tradeOrderRepository, never()).save(any(TradeOrder.class));
    }

    @Test
    void previewBuyShouldReturnPriceTotalAndMaxWholeShares() {
        when(portfolioRepository.findByCustomer_UserId(1L)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(100L)).thenReturn(Optional.of(instrument));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("222.50"));
        when(preTradeMarketService.getTradability("AAPL")).thenReturn(tradable());

        OrderPreviewResponse preview = tradeOrderService.previewOrder(1L, 100L, "BUY", new BigDecimal("10"));

        assertTrue(preview.isCanSubmit());
        assertNull(preview.getMessage());
        assertEquals(new BigDecimal("222.50"), preview.getIndicativePriceUsd());
        assertEquals(new BigDecimal("2225.00"), preview.getEstimatedTotalUsd());
        assertEquals(new BigDecimal("10000.00"), preview.getCashAvailableUsd());
        assertEquals(new BigDecimal("44"), preview.getMaxQuantity()); // floor(10000 / 222.50)
        assertEquals("NASDAQ", preview.getMarketName());
        assertTrue(preview.isMarketOpen());
    }

    @Test
    void previewBuyShouldExplainWhenCashIsInsufficient() {
        when(portfolioRepository.findByCustomer_UserId(1L)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(100L)).thenReturn(Optional.of(instrument));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("222.50"));
        when(preTradeMarketService.getTradability("AAPL")).thenReturn(tradable());

        OrderPreviewResponse preview = tradeOrderService.previewOrder(1L, 100L, "BUY", new BigDecimal("50"));

        assertFalse(preview.isCanSubmit());
        assertEquals("Not enough cash. This order costs about $11,125.00 and you have $10,000.00 available.", preview.getMessage());
        assertEquals(new BigDecimal("11125.00"), preview.getEstimatedTotalUsd());
    }

    @Test
    void previewSellShouldCapMaxAtQuantityOwned() {
        Holding holding = new Holding();
        holding.setQuantity(new BigDecimal("3"));

        when(portfolioRepository.findByCustomer_UserId(1L)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(100L)).thenReturn(Optional.of(instrument));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("222.50"));
        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(1L, 100L)).thenReturn(Optional.of(holding));
        when(preTradeMarketService.getTradability("AAPL")).thenReturn(tradable());

        OrderPreviewResponse preview = tradeOrderService.previewOrder(1L, 100L, "SELL", new BigDecimal("5"));

        assertFalse(preview.isCanSubmit());
        assertEquals("You only own 3 AAPL.", preview.getMessage());
        assertEquals(new BigDecimal("3"), preview.getMaxQuantity());
    }

    @Test
    void previewWithoutQuantityShouldStillReturnPriceButNotAllowSubmit() {
        when(portfolioRepository.findByCustomer_UserId(1L)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(100L)).thenReturn(Optional.of(instrument));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("222.50"));
        when(preTradeMarketService.getTradability("AAPL")).thenReturn(tradable());

        OrderPreviewResponse preview = tradeOrderService.previewOrder(1L, 100L, "BUY", null);

        assertEquals(new BigDecimal("222.50"), preview.getIndicativePriceUsd());
        assertNull(preview.getEstimatedTotalUsd());
        assertFalse(preview.isCanSubmit());
        assertEquals("Enter a quantity greater than zero.", preview.getMessage());
    }

    @Test
    void previewShouldReportClosedMarket() {
        when(portfolioRepository.findByCustomer_UserId(1L)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(100L)).thenReturn(Optional.of(instrument));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("222.50"));
        when(preTradeMarketService.getTradability("AAPL")).thenReturn(marketClosed());

        OrderPreviewResponse preview = tradeOrderService.previewOrder(1L, 100L, "BUY", new BigDecimal("1"));

        assertFalse(preview.isCanSubmit());
        assertFalse(preview.isMarketOpen());
        assertTrue(preview.getMessage().startsWith("NASDAQ is closed right now"));
        // Price is still shown so the client can plan the trade
        assertEquals(new BigDecimal("222.50"), preview.getIndicativePriceUsd());
    }

    @Test
    void orderHistoryShouldBeScopedToCustomerNewestFirstWithFillDetails() {
        TradeOrder filled = historyOrder(2L, OrderStatus.FILLED);
        TradeOrder rejected = historyOrder(1L, OrderStatus.REJECTED);
        rejected.setRejectionReason("Not enough cash.");

        Trade trade = new Trade();
        trade.setOrder(filled);
        trade.setExecutionPriceUsd(new BigDecimal("220.00"));
        trade.setTotalUsdValue(new BigDecimal("440.00"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(tradeOrderRepository.findByPortfolio_Customer_UserId(eq(1L), pageableCaptor.capture()))
            .thenAnswer(invocation -> new PageImpl<>(List.of(filled, rejected), invocation.getArgument(1), 2));
        when(tradeRepository.findByOrderOrderIdIn(List.of(2L, 1L))).thenReturn(List.of(trade));

        OrderHistoryResponse history = tradeOrderService.getOrderHistory(1L, 0, 20);

        Sort.Order sortOrder = pageableCaptor.getValue().getSort().getOrderFor("submittedAt");
        assertNotNull(sortOrder);
        assertTrue(sortOrder.isDescending());

        assertEquals(2, history.getOrders().size());
        assertEquals(2L, history.getTotalElements());
        assertFalse(history.isHasMore());

        OrderResponse first = history.getOrders().get(0);
        assertEquals("FILLED", first.getOrderStatus());
        assertEquals("Apple Inc.", first.getInstrumentName());
        assertEquals(new BigDecimal("220.00"), first.getExecutionPrice());
        assertEquals(new BigDecimal("440.00"), first.getTotalUsdValue());

        OrderResponse second = history.getOrders().get(1);
        assertEquals("REJECTED", second.getOrderStatus());
        assertEquals("Not enough cash.", second.getRejectionReason());
        assertNull(second.getExecutionPrice());
    }

    @Test
    void orderHistoryShouldCapPageSize() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(tradeOrderRepository.findByPortfolio_Customer_UserId(eq(1L), pageableCaptor.capture()))
            .thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));

        OrderHistoryResponse history = tradeOrderService.getOrderHistory(1L, -3, 5000);

        assertEquals(100, pageableCaptor.getValue().getPageSize());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertTrue(history.getOrders().isEmpty());
        verify(tradeRepository, never()).findByOrderOrderIdIn(any());
    }

    private TradeOrder historyOrder(Long orderId, OrderStatus status) {
        TradeOrder order = new TradeOrder();
        order.setOrderId(orderId);
        order.setPortfolio(portfolio);
        order.setInstrument(instrument);
        order.setOrderAction(OrderAction.BUY);
        order.setOrderType(OrderType.MARKET);
        order.setTimeInForce(TimeInForce.DAY);
        order.setOrderStatus(status);
        order.setQuantity(new BigDecimal("2"));
        return order;
    }
}
