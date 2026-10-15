package com.leaping.portfolio_app.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.audit.enums.AuditEventType;
import com.leaping.portfolio_app.audit.service.AuditService;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Price;
import com.leaping.portfolio_app.market.enums.PriceSourceType;
import com.leaping.portfolio_app.market.service.PricingService;
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
    private PricingService pricingService;

    @Mock
    private AuditService auditService;

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
        User user = new User();
        user.setId(1L);
        customer.setUser(user);
        portfolio.setCustomer(customer);

        Currency usd = new Currency();
        usd.setCurrencyCode("USD");
        usd.setCurrentExchangeRateToUsd(BigDecimal.ONE);

        instrument = new Instrument();
        instrument.setInstrumentId(100L);
        instrument.setSymbol("AAPL");
        instrument.setPriceCurrency(usd);

        lenient().when(pricingService.getLatestPriceBySymbol("AAPL"))
            .thenReturn(Optional.of(price(new BigDecimal("100.00"), usd)));
        lenient().when(executionAttemptRepository.save(any(ExecutionAttempt.class)))
            .thenAnswer(invocation -> {
                ExecutionAttempt attempt = invocation.getArgument(0);
                attempt.setExecutionAttemptId(200L);
                return attempt;
            });
    }

    @Test
    void executeTradeBuyShouldUpdateHoldingAndCashAndFillOrder() {
        Currency gbp = new Currency();
        gbp.setCurrencyCode("GBP");
        gbp.setCurrentExchangeRateToUsd(new BigDecimal("1.25"));
        instrument.setPriceCurrency(gbp);
        when(pricingService.getLatestPriceBySymbol("AAPL"))
            .thenReturn(Optional.of(price(new BigDecimal("80.00"), gbp)));

        TradeOrder order = new TradeOrder();
        order.setOrderId(10L);
        order.setPortfolio(portfolio);
        order.setInstrument(instrument);
        order.setOrderAction(OrderAction.BUY);
        order.setOrderType(OrderType.MARKET);
        order.setOrderStatus(OrderStatus.SUBMITTED);
        order.setQuantity(new BigDecimal("2"));

        Holding existing = new Holding();
        existing.setHoldingId(500L);
        existing.setPortfolio(portfolio);
        existing.setInstrument(instrument);
        existing.setQuantity(new BigDecimal("5"));
        existing.setAverageCostUsd(new BigDecimal("90.00"));

        when(tradeOrderRepository.findById(10L)).thenReturn(Optional.of(order));
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
        when(cashTransactionRepository.save(any(CashTransaction.class))).thenAnswer(invocation -> {
            CashTransaction transaction = invocation.getArgument(0);
            transaction.setCashTransactionId(400L);
            transaction.setCreatedAt(OffsetDateTime.parse("2026-10-14T10:00:02Z"));
            return transaction;
        });
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeConfirmationResponse response = tradeService.executeTrade(10L);

        assertNotNull(response);
        assertEquals(300L, response.getTradeId());
        assertEquals(10L, response.getOrderId());
        assertEquals("BUY", response.getOrderAction());
        assertEquals(
            0,
            new BigDecimal("200.00").compareTo(response.getTotalUsdValue())
        );

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

        ArgumentCaptor<ExecutionAttempt> attemptCaptor =
            ArgumentCaptor.forClass(ExecutionAttempt.class);
        verify(executionAttemptRepository).save(attemptCaptor.capture());
        ExecutionAttempt attempt = attemptCaptor.getValue();
        assertEquals(new BigDecimal("80.00"), attempt.getQuotedPrice());
        assertEquals("GBP", attempt.getPriceCurrency().getCurrencyCode());
        assertEquals(new BigDecimal("1.25"), attempt.getExchangeRateToUsd());
        assertEquals(new BigDecimal("100.00000000"), attempt.getQuotedPriceUsd());
        assertEquals(PriceSourceType.API, attempt.getSourceType());
        assertEquals("test-provider", attempt.getProviderName());

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(tradeCaptor.capture());
        Trade savedTrade = tradeCaptor.getValue();
        assertEquals(new BigDecimal("80.00"), savedTrade.getExecutionPrice());
        assertEquals(new BigDecimal("100.00000000"), savedTrade.getExecutionPriceUsd());
        assertEquals(new BigDecimal("200.00000000"), savedTrade.getTotalUsdValue());
        assertEquals(new BigDecimal("1.25"), savedTrade.getExchangeRateToUsdAtExecution());

        ArgumentCaptor<Map<String, Object>> priceDetails =
            ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> holdingDetails =
            ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> cashDetails =
            ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> tradeDetails =
            ArgumentCaptor.forClass(Map.class);
        var auditOrder = inOrder(auditService);
        auditOrder.verify(auditService).recordEvent(
            eq(AuditEventType.PRICE_CAPTURED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            eq(attempt),
            isNull(),
            isNull(),
            priceDetails.capture()
        );
        auditOrder.verify(auditService).recordEvent(
            eq(AuditEventType.HOLDING_CHANGED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            eq(attempt),
            eq(savedTrade),
            isNull(),
            holdingDetails.capture()
        );
        auditOrder.verify(auditService).recordEvent(
            eq(AuditEventType.CASH_BALANCE_CHANGED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            eq(attempt),
            eq(savedTrade),
            eq(cashCaptor.getValue()),
            cashDetails.capture()
        );
        auditOrder.verify(auditService).recordEvent(
            eq(AuditEventType.TRADE_FILLED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            eq(attempt),
            eq(savedTrade),
            isNull(),
            tradeDetails.capture()
        );

        assertEquals(List.of(
            "GBP",
            "test-provider",
            new BigDecimal("80.00"),
            new BigDecimal("1.25"),
            new BigDecimal("100.00000000")
        ), List.of(
            priceDetails.getValue().get("priceCurrencyCode"),
            priceDetails.getValue().get("providerName"),
            priceDetails.getValue().get("quotedPrice"),
            priceDetails.getValue().get("exchangeRateToUsd"),
            priceDetails.getValue().get("quotedPriceUsd")
        ));
        assertEquals(new BigDecimal("5"), holdingDetails.getValue().get("quantityBefore"));
        assertEquals(new BigDecimal("7"), holdingDetails.getValue().get("quantityAfter"));
        assertEquals(new BigDecimal("90.00"), holdingDetails.getValue().get("averageCostUsdBefore"));
        assertEquals(new BigDecimal("92.85714286"), holdingDetails.getValue().get("averageCostUsdAfter"));
        assertEquals(new BigDecimal("-200.00"), cashDetails.getValue().get("amountUsd"));
        assertEquals(new BigDecimal("1000.00"), cashDetails.getValue().get("balanceBeforeUsd"));
        assertEquals(new BigDecimal("800.00"), cashDetails.getValue().get("balanceAfterUsd"));
        assertEquals(300L, tradeDetails.getValue().get("tradeId"));
        assertEquals(200L, tradeDetails.getValue().get("executionAttemptId"));
        assertEquals("FILLED", tradeDetails.getValue().get("orderStatusAfter"));
        verify(auditService, times(4)).recordEvent(
            any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void executeTradeNewHoldingUsesUsdExecutionCostAndAuditsCreation() {
        Currency eur = new Currency();
        eur.setCurrencyCode("EUR");
        eur.setCurrentExchangeRateToUsd(new BigDecimal("1.10"));
        instrument.setPriceCurrency(eur);
        when(pricingService.getLatestPriceBySymbol("AAPL"))
            .thenReturn(Optional.of(price(new BigDecimal("10.00"), eur)));

        TradeOrder order = marketOrder(15L, OrderAction.BUY, "3");
        when(tradeOrderRepository.findById(15L)).thenReturn(Optional.of(order));
        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(1L, 100L))
            .thenReturn(Optional.empty());
        when(executionAttemptRepository.save(any(ExecutionAttempt.class))).thenAnswer(invocation -> {
            ExecutionAttempt attempt = invocation.getArgument(0);
            attempt.setExecutionAttemptId(210L);
            return attempt;
        });
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setTradeId(310L);
            return trade;
        });
        when(holdingRepository.save(any(Holding.class))).thenAnswer(invocation -> {
            Holding holding = invocation.getArgument(0);
            holding.setHoldingId(610L);
            return holding;
        });
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashTransactionRepository.save(any(CashTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tradeService.executeTrade(15L);

        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());
        assertEquals(new BigDecimal("3"), holdingCaptor.getValue().getQuantity());
        assertEquals(new BigDecimal("11.00000000"), holdingCaptor.getValue().getAverageCostUsd());

        ArgumentCaptor<Map<String, Object>> detailsCaptor =
            ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordEvent(
            eq(AuditEventType.HOLDING_CHANGED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            any(ExecutionAttempt.class),
            any(Trade.class),
            isNull(),
            detailsCaptor.capture()
        );
        assertEquals(610L, detailsCaptor.getValue().get("holdingId"));
        assertEquals(new BigDecimal("0"), detailsCaptor.getValue().get("quantityBefore"));
        assertEquals(new BigDecimal("3"), detailsCaptor.getValue().get("quantityAfter"));
        assertEquals(new BigDecimal("11.00000000"), detailsCaptor.getValue().get("averageCostUsdAfter"));
    }

    @Test
    void executeTradeFullSellShouldDeleteHoldingInsteadOfSavingZeroQuantity() {
        TradeOrder order = marketOrder(14L, OrderAction.SELL, "5");

        Holding existing = new Holding();
        existing.setHoldingId(501L);
        existing.setPortfolio(portfolio);
        existing.setInstrument(instrument);
        existing.setQuantity(new BigDecimal("5"));
        existing.setAverageCostUsd(new BigDecimal("90.00"));

        when(tradeOrderRepository.findById(14L)).thenReturn(Optional.of(order));
        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(1L, 100L))
            .thenReturn(Optional.of(existing));
        when(executionAttemptRepository.save(any(ExecutionAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashTransactionRepository.save(any(CashTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeConfirmationResponse response = tradeService.executeTrade(14L);

        assertEquals(
            0,
            new BigDecimal("500.00").compareTo(response.getTotalUsdValue())
        );
        assertEquals(new BigDecimal("1500.00"), portfolio.getCashBalanceUsd());
        assertEquals(OrderStatus.FILLED, order.getOrderStatus());
        verify(holdingRepository).delete(existing);
        verify(holdingRepository, never()).save(any(Holding.class));

        verify(auditService).recordEvent(
            eq(AuditEventType.CASH_BALANCE_CHANGED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            any(ExecutionAttempt.class),
            any(Trade.class),
            any(CashTransaction.class),
            any(Map.class)
        );
        verify(auditService).recordEvent(
            eq(AuditEventType.TRADE_FILLED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            any(ExecutionAttempt.class),
            any(Trade.class),
            isNull(),
            any(Map.class)
        );

        ArgumentCaptor<Map<String, Object>> detailsCaptor =
            ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordEvent(
            eq(AuditEventType.HOLDING_CHANGED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            any(ExecutionAttempt.class),
            any(Trade.class),
            isNull(),
            detailsCaptor.capture()
        );
        assertEquals(501L, detailsCaptor.getValue().get("holdingId"));
        assertEquals(new BigDecimal("5"), detailsCaptor.getValue().get("quantityBefore"));
        assertEquals(new BigDecimal("0"), detailsCaptor.getValue().get("quantityAfter"));
        assertEquals(new BigDecimal("90.00"), detailsCaptor.getValue().get("averageCostUsdBefore"));
        assertNull(detailsCaptor.getValue().get("averageCostUsdAfter"));
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
        when(pricingService.getLatestPriceBySymbol("AAPL"))
            .thenReturn(Optional.empty());
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeRejectedException ex = assertThrows(
            TradeRejectedException.class,
            () -> tradeService.executeTrade(77L)
        );

        assertEquals("We don't have a current price for AAPL, so the order couldn't be filled.", ex.getMessage());
        assertEquals(OrderStatus.REJECTED, order.getOrderStatus());
        assertEquals(ex.getMessage(), order.getRejectionReason());
        assertNotNull(order.getRejectedAt());
        verifyNoInteractions(tradeRepository, cashTransactionRepository);
        verify(executionAttemptRepository).save(any(ExecutionAttempt.class));

        ArgumentCaptor<ExecutionAttempt> attemptCaptor =
            ArgumentCaptor.forClass(ExecutionAttempt.class);
        verify(executionAttemptRepository).save(attemptCaptor.capture());
        ExecutionAttempt failedAttempt = attemptCaptor.getValue();
        assertEquals(ExecutionAttemptStatus.FAILED, failedAttempt.getAttemptStatus());
        assertEquals(ex.getMessage(), failedAttempt.getReason());

        ArgumentCaptor<Map<String, Object>> priceDetails =
            ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> rejectionDetails =
            ArgumentCaptor.forClass(Map.class);
        var auditOrder = inOrder(auditService);
        auditOrder.verify(auditService).recordEvent(
            eq(AuditEventType.PRICE_CAPTURE_FAILED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            eq(failedAttempt),
            isNull(),
            isNull(),
            priceDetails.capture()
        );
        auditOrder.verify(auditService).recordEvent(
            eq(AuditEventType.ORDER_REJECTED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            eq(failedAttempt),
            isNull(),
            isNull(),
            rejectionDetails.capture()
        );
        assertEquals("FAILED", priceDetails.getValue().get("attemptStatus"));
        assertEquals(ex.getMessage(), priceDetails.getValue().get("reason"));
        assertEquals(ex.getMessage(), rejectionDetails.getValue().get("reason"));
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
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeRejectedException ex = assertThrows(TradeRejectedException.class, () -> tradeService.executeTrade(11L));

        assertEquals("Not enough cash. This order costs $200.00 and you have $150.00 available.", ex.getMessage());
        assertEquals(OrderStatus.REJECTED, order.getOrderStatus());
        assertEquals(new BigDecimal("150.00"), portfolio.getCashBalanceUsd());
        verifyNoInteractions(tradeRepository, cashTransactionRepository);
        verify(executionAttemptRepository).save(any(ExecutionAttempt.class));
        verify(holdingRepository, never()).save(any(Holding.class));

        ArgumentCaptor<ExecutionAttempt> attemptCaptor =
            ArgumentCaptor.forClass(ExecutionAttempt.class);
        verify(executionAttemptRepository).save(attemptCaptor.capture());
        ArgumentCaptor<Map<String, Object>> rejectionDetails =
            ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordEvent(
            eq(AuditEventType.ORDER_REJECTED),
            eq(portfolio.getCustomer().getUser()),
            eq(order),
            eq(attemptCaptor.getValue()),
            isNull(),
            isNull(),
            rejectionDetails.capture()
        );
        assertEquals(ex.getMessage(), rejectionDetails.getValue().get("reason"));
    }

    @Test
    void executeTradeShouldRejectSellWhenHoldingInsufficient() {
        TradeOrder order = marketOrder(12L, OrderAction.SELL, "10");

        Holding existing = new Holding();
        existing.setPortfolio(portfolio);
        existing.setInstrument(instrument);
        existing.setQuantity(new BigDecimal("3"));

        when(tradeOrderRepository.findById(12L)).thenReturn(Optional.of(order));
        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(1L, 100L)).thenReturn(Optional.of(existing));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeRejectedException ex = assertThrows(TradeRejectedException.class, () -> tradeService.executeTrade(12L));

        assertEquals("You only own 3 AAPL.", ex.getMessage());
        assertEquals(OrderStatus.REJECTED, order.getOrderStatus());
        assertEquals(new BigDecimal("3"), existing.getQuantity());
        verifyNoInteractions(tradeRepository, cashTransactionRepository);
        verify(executionAttemptRepository).save(any(ExecutionAttempt.class));
    }

    @Test
    void executeTradeSettlementFailureDoesNotRecordSuccessfulSettlementAudit() {
        TradeOrder order = marketOrder(16L, OrderAction.BUY, "2");
        Holding existing = new Holding();
        existing.setQuantity(new BigDecimal("1"));
        existing.setAverageCostUsd(new BigDecimal("90.00"));

        when(tradeOrderRepository.findById(16L)).thenReturn(Optional.of(order));
        when(executionAttemptRepository.save(any(ExecutionAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(1L, 100L))
            .thenReturn(Optional.of(existing));
        when(holdingRepository.save(any(Holding.class)))
            .thenThrow(new RuntimeException("holding settlement failed"));

        assertThrows(RuntimeException.class, () -> tradeService.executeTrade(16L));

        verify(auditService, never()).recordEvent(
            eq(AuditEventType.HOLDING_CHANGED),
            any(), any(), any(), any(), any(), any()
        );
        verify(auditService, never()).recordEvent(
            eq(AuditEventType.CASH_BALANCE_CHANGED),
            any(), any(), any(), any(), any(), any()
        );
        verify(auditService, never()).recordEvent(
            eq(AuditEventType.TRADE_FILLED),
            any(), any(), any(), any(), any(), any()
        );
        verifyNoInteractions(cashTransactionRepository, portfolioRepository);
    }

    @Test
    void executeTradeShouldRefuseOrderThatIsNotSubmitted() {
        TradeOrder order = marketOrder(13L, OrderAction.BUY, "1");
        order.setOrderStatus(OrderStatus.FILLED);

        when(tradeOrderRepository.findById(13L)).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> tradeService.executeTrade(13L));
        verify(entityManager, never()).refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(
            tradeRepository,
            executionAttemptRepository,
            holdingRepository,
            cashTransactionRepository,
            auditService
        );
    }

    private Price price(BigDecimal nativePrice, Currency currency) {
        return new Price(
            instrument,
            nativePrice,
            currency,
            OffsetDateTime.parse("2026-10-14T10:00:00Z"),
            PriceSourceType.API,
            "test-provider"
        );
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
