package com.leaping.portfolio_app.trade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.market.dto.TradabilityDto;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.market.enums.TradabilityReason;
import com.leaping.portfolio_app.market.service.BidAskQuote;
import com.leaping.portfolio_app.market.service.PreTradeMarketService;
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
import com.leaping.portfolio_app.trade.enums.OrderType;
import com.leaping.portfolio_app.trade.enums.TimeInForce;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

/**
 * TradeOrderService handles order placement and validation.
 * 
 * Responsibilities:
 * - Validate order parameters (quantity, price, cash availability)
 * - Create and persist TradeOrder records
 * - Reject orders that don't meet validation criteria
 * - Map between DTOs and entities
 */
@Service
@Transactional
public class TradeOrderService {
    private final TradeOrderRepository tradeOrderRepository;
    private final PortfolioRepository portfolioRepository;
    private final InstrumentRepository instrumentRepository;
    private final HoldingRepository holdingRepository;
    private final MarketPriceService priceService;
    private final PreTradeMarketService preTradeMarketService;
    private final TradeRepository tradeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    /**
     * Enforce market hours and price freshness when accepting orders (BR-05).
     * Can be switched off locally (trading.enforce-tradability: false) to test trades
     * outside market hours. Must stay on in any shared environment.
     */
    @Value("${trading.enforce-tradability:true}")
    private boolean enforceTradability = true;

    public TradeOrderService(
            TradeOrderRepository tradeOrderRepository,
            PortfolioRepository portfolioRepository,
            InstrumentRepository instrumentRepository,
            HoldingRepository holdingRepository,
            MarketPriceService priceService,
            PreTradeMarketService preTradeMarketService,
            TradeRepository tradeRepository,
            ApplicationEventPublisher eventPublisher,
            EntityManager entityManager
    ) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.portfolioRepository = portfolioRepository;
        this.instrumentRepository = instrumentRepository;
        this.holdingRepository = holdingRepository;
        this.priceService = priceService;
        this.preTradeMarketService = preTradeMarketService;
        this.tradeRepository = tradeRepository;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    /**
     * Place a new order on behalf of a customer.
     *
     * Lifecycle, all recorded in the permanent record (BR-14):
     * 1. The order is recorded as SUBMITTED as soon as it is received.
     * 2. It is checked against the firm's trading rules (BR-05).
     * 3. If it fails, it is REJECTED with a client-readable reason.
     *    If it passes, it is ACCEPTED: a firm commitment to execute.
     *
     * Execution is not attempted here. Once this transaction commits, OrderExecutionWorker
     * executes accepted orders asynchronously, so the acceptance is always persisted first.
     *
     * @param customerId The customer placing the order (identified by User ID)
     * @param request The order request with instrument, side, type and quantity
     * @return The order, ACCEPTED or REJECTED (see its rejection reason)
     * @throws IllegalArgumentException if the request is malformed; nothing is recorded
     */
    public TradeOrder placeOrder(Long customerId, PlaceOrderRequest request) {
        // 1. Load portfolio for customer
        Portfolio portfolio = portfolioRepository.findByCustomer_UserId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found for customer"));

        // 2. Load instrument
        Instrument instrument = instrumentRepository.findById(request.getInstrumentId())
            .orElseThrow(() -> new IllegalArgumentException("Instrument not found"));

        // 3. Check the request is a well-formed order
        validateOrderTerms(request);

        // 4. Record the order as received, with the price quoted at submission
        TradeOrder order = new TradeOrder(
            portfolio,
            instrument,
            OrderAction.valueOf(request.getOrderAction().toUpperCase()),
            OrderType.valueOf(request.getOrderType().toUpperCase()),
            TimeInForce.valueOf(request.getTimeInForce() != null ? request.getTimeInForce().toUpperCase() : "DAY"),
            request.getQuantity(),
            request.getLimitPrice()
        );
        order.setReferencePriceUsd(quotedPrice(instrument, order.getOrderAction()));
        order = tradeOrderRepository.save(order);

        // 5. Pre-trade checks: reject, or accept as a firm commitment
        String issue = findPreTradeIssue(order, portfolio, instrument);
        if (issue != null) {
            order.reject(issue);
            return tradeOrderRepository.save(order);
        }
        order.accept();
        order = tradeOrderRepository.save(order);
        // Market orders execute straight away; limit orders rest until their price is reached
        if (OrderType.MARKET.equals(order.getOrderType())) {
            eventPublisher.publishEvent(new OrderAcceptedEvent(order.getOrderId()));
        }
        return order;
    }

    /**
     * Structural checks on the request. A request that fails these is not an order at all,
     * so it is refused without being recorded.
     *
     * @throws IllegalArgumentException with a client-readable reason
     */
    private void validateOrderTerms(PlaceOrderRequest request) {
        if (request.getOrderAction() == null || request.getOrderType() == null) {
            throw new IllegalArgumentException("Order action and order type are required");
        }
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Enter a quantity greater than zero.");
        }

        OrderType orderType = OrderType.valueOf(request.getOrderType().toUpperCase());
        if (OrderType.LIMIT.equals(orderType)) {
            if (request.getLimitPrice() == null) {
                throw new IllegalArgumentException("Limit price is required for LIMIT orders");
            }
            if (request.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Limit price must be positive");
            }
        }
    }

    /**
     * Check a recorded order against the firm's trading rules (BR-05).
     *
     * Checks:
     * - Instrument is currently tradable (active, market open, fresh price)
     * - For BUY orders: customer has sufficient cash
     * - For SELL orders: customer holds enough of the instrument
     *
     * @return a client-readable reason the order must be rejected, or null to accept it
     */
    private String findPreTradeIssue(TradeOrder order, Portfolio portfolio, Instrument instrument) {
        // BUY cash check uses the limit price if given, otherwise the ask quoted at submission
        BigDecimal price = order.getLimitPriceUsd() != null
            ? order.getLimitPriceUsd()
            : order.getReferencePriceUsd();

        return findBlockingIssue(
            instrument,
            order.getOrderAction(),
            order.getQuantity(),
            price,
            portfolio.getCashBalanceUsd(),
            getQuantityOwned(portfolio, instrument),
            enforceTradability ? preTradeMarketService.getTradability(instrument.getSymbol()) : null
        );
    }

    /**
     * Preview an order before it is submitted (BR-13): indicative price, estimated
     * total, what the client has available, and whether the order would be accepted.
     * 
     * Uses the same rules as placeOrder, so can_submit=false means placement would fail.
     * 
     * @param customerId The customer (User ID)
     * @param instrumentId The instrument to trade
     * @param orderAction "BUY" or "SELL"
     * @param quantity The quantity, or null if the client hasn't entered one yet
     */
    @Transactional(readOnly = true)
    public OrderPreviewResponse previewOrder(Long customerId, Long instrumentId, String orderAction, BigDecimal quantity) {
        Portfolio portfolio = portfolioRepository.findByCustomer_UserId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found for customer"));
        Instrument instrument = instrumentRepository.findById(instrumentId)
            .orElseThrow(() -> new IllegalArgumentException("Instrument not found"));
        OrderAction action = OrderAction.valueOf(orderAction.toUpperCase());

        BidAskQuote quote = currentQuote(instrument);
        // Buyers are quoted the ask and sellers the bid: the side the order will fill at
        BigDecimal price = quote != null ? quote.priceFor(OrderAction.BUY.equals(action)) : null;
        BigDecimal cash = portfolio.getCashBalanceUsd();
        BigDecimal owned = getQuantityOwned(portfolio, instrument);
        TradabilityDto tradability = preTradeMarketService.getTradability(instrument.getSymbol());

        OrderPreviewResponse preview = new OrderPreviewResponse();
        preview.setInstrumentId(instrument.getInstrumentId());
        preview.setSymbol(instrument.getSymbol());
        preview.setName(instrument.getName());
        preview.setInstrumentType(instrument.getInstrumentType() != null ? instrument.getInstrumentType().name() : null);
        preview.setMarketName(instrument.getMarket() != null ? instrument.getMarket().getMarketName() : null);
        preview.setOrderAction(action.name());
        preview.setQuantity(quantity);
        preview.setIndicativePriceUsd(price);
        if (quote != null) {
            preview.setBidUsd(quote.bid());
            preview.setAskUsd(quote.ask());
            preview.setSpreadUsd(quote.spread());
        }
        preview.setCashAvailableUsd(cash);
        preview.setQuantityOwned(owned);
        preview.setTradable(tradability.isTradable());
        preview.setMarketOpen(tradability.isMarketOpen() || tradability.isAlwaysOpen());

        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            if (quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                preview.setEstimatedTotalUsd(price.multiply(quantity).setScale(2, RoundingMode.HALF_UP));
            }
            if (OrderAction.BUY.equals(action)) {
                // Whole shares for stocks; fractional units for crypto and FX
                int scale = InstrumentType.STOCK.equals(instrument.getInstrumentType()) ? 0 : 4;
                preview.setMaxQuantity(cash.divide(price, scale, RoundingMode.DOWN));
            }
        }
        if (OrderAction.SELL.equals(action)) {
            preview.setMaxQuantity(owned);
        }

        String issue = findBlockingIssue(
            instrument, action, quantity, price, cash, owned,
            enforceTradability ? tradability : null
        );
        preview.setCanSubmit(issue == null);
        preview.setMessage(issue);
        return preview;
    }

    /**
     * The single set of trading rules shared by placement and preview.
     * Returns a plain-language reason the order can't go ahead, or null if it can.
     * Pass tradability as null to skip the market-hours/price-freshness check.
     */
    private String findBlockingIssue(
            Instrument instrument,
            OrderAction action,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal cash,
            BigDecimal owned,
            TradabilityDto tradability
    ) {
        String symbol = instrument.getSymbol();

        if (tradability != null && !tradability.isTradable()) {
            return describeUntradable(instrument, tradability.getReason());
        }

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return "Enter a quantity greater than zero.";
        }

        if (OrderAction.BUY.equals(action)) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                return noLivePriceMessage(symbol);
            }
            BigDecimal required = price.multiply(quantity);
            if (cash.compareTo(required) < 0) {
                return String.format(
                    "Not enough cash. This order costs about %s and you have %s available.",
                    formatUsd(required), formatUsd(cash));
            }
        } else {
            if (owned.compareTo(BigDecimal.ZERO) <= 0) {
                return String.format("You don't own any %s to sell.", symbol);
            }
            if (owned.compareTo(quantity) < 0) {
                return String.format("You only own %s %s.", owned.stripTrailingZeros().toPlainString(), symbol);
            }
        }
        return null;
    }

    private String describeUntradable(Instrument instrument, TradabilityReason reason) {
        String symbol = instrument.getSymbol();
        Market market = instrument.getMarket();
        if (reason == TradabilityReason.MARKET_CLOSED && market != null) {
            if (market.getOpenTime() != null && market.getCloseTime() != null) {
                return String.format("%s is closed right now. It trades %s\u2013%s (%s).",
                    market.getMarketName(), market.getOpenTime(), market.getCloseTime(), market.getTimezone());
            }
            return String.format("%s is closed right now.", market.getMarketName());
        }
        if (reason == TradabilityReason.PRICE_UNAVAILABLE || reason == TradabilityReason.PRICE_STALE) {
            return noLivePriceMessage(symbol);
        }
        return String.format("%s isn't available to trade.", symbol);
    }

    /**
     * Orders are refused rather than taken without a live price (BR-05), so a client is never
     * filled at a price they weren't shown. The message says so, rather than just "not available".
     */
    private static String noLivePriceMessage(String symbol) {
        return String.format(
            "We don't have a live price for %s right now, so we can't take orders for it. "
                + "Orders are only accepted against a live price, so you're never filled at a price you haven't seen. "
                + "Please try again shortly.",
            symbol);
    }

    /** Two-sided quote from the same price source execution uses (BR-08), or null if there is no price. */
    private BidAskQuote currentQuote(Instrument instrument) {
        return BidAskQuote.fromMid(priceService.getCurrentPrice(instrument.getSymbol()), instrument.getInstrumentType());
    }

    /** The price an order is quoted at: the ask for a buy, the bid for a sell. */
    private BigDecimal quotedPrice(Instrument instrument, OrderAction action) {
        BidAskQuote quote = currentQuote(instrument);
        return quote != null ? quote.priceFor(OrderAction.BUY.equals(action)) : null;
    }

    private BigDecimal getQuantityOwned(Portfolio portfolio, Instrument instrument) {
        return holdingRepository
            .findByPortfolio_PortfolioIdAndInstrument_InstrumentId(
                portfolio.getPortfolioId(), instrument.getInstrumentId())
            .map(Holding::getQuantity)
            .orElse(BigDecimal.ZERO);
    }

    private static String formatUsd(BigDecimal amount) {
        return String.format("$%,.2f", amount);
    }

    /**
     * Get order details by order ID.
     * 
     * @param orderId The ID of the order to retrieve
     * @param customerId The customer ID (for ownership verification)
     * @return The OrderResponse DTO
     * @throws IllegalArgumentException if order not found or doesn't belong to customer
     */
    public OrderResponse getOrder(Long orderId, Long customerId) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Verify ownership
        if (!order.getPortfolio().getCustomer().getUserId().equals(customerId)) {
            throw new IllegalArgumentException("Unauthorized access to order");
        }

        OrderResponse response = toOrderResponse(order);
        tradeRepository.findByOrderOrderId(orderId).ifPresent(trade -> applyFill(response, trade));
        return response;
    }

    /**
     * Get the customer's order history (blotter), newest first (BR-11).
     * Only the customer's own orders are returned (BR-02).
     * 
     * @param customerId The customer (User ID)
     * @param page Zero-based page number
     * @param size Page size (capped at 100)
     */
    @Transactional(readOnly = true)
    public OrderHistoryResponse getOrderHistory(Long customerId, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageRequest = PageRequest.of(
            Math.max(page, 0),
            pageSize,
            Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("orderId"))
        );
        Page<TradeOrder> orders = tradeOrderRepository.findByPortfolio_Customer_UserId(customerId, pageRequest);

        // One query for all fills on this page rather than one per order
        Map<Long, Trade> tradesByOrderId = orders.isEmpty()
            ? Map.of()
            : tradeRepository.findByOrderOrderIdIn(orders.map(TradeOrder::getOrderId).toList()).stream()
                .collect(Collectors.toMap(trade -> trade.getOrder().getOrderId(), Function.identity()));

        List<OrderResponse> items = orders.stream()
            .map(order -> {
                OrderResponse response = toOrderResponse(order);
                Trade trade = tradesByOrderId.get(order.getOrderId());
                if (trade != null) {
                    applyFill(response, trade);
                }
                return response;
            })
            .toList();

        return new OrderHistoryResponse(items, orders.getNumber(), pageSize, orders.getTotalElements(), orders.hasNext());
    }

    private void applyFill(OrderResponse response, Trade trade) {
        response.setExecutionPrice(trade.getExecutionPriceUsd());
        response.setTotalUsdValue(trade.getTotalUsdValue());
    }

    /**
     * Cancel an open order (SUBMITTED or ACCEPTED) that has not been filled yet.
     * The order row is locked, so a cancel and an in-flight execution can't both succeed.
     * 
     * @param orderId The ID of the order to cancel
     * @param customerId The customer ID (for ownership verification)
     * @return The updated OrderResponse DTO
     * @throws IllegalArgumentException if order not found, doesn't belong to customer, or can't be cancelled
     */
    public OrderResponse cancelOrder(Long orderId, Long customerId) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Verify ownership
        if (!order.getPortfolio().getCustomer().getUserId().equals(customerId)) {
            throw new IllegalArgumentException("Unauthorized access to order");
        }

        // Re-read under a row lock: execution may be filling it right now
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        if (!order.getOrderStatus().isOpen()) {
            throw new IllegalArgumentException(
                String.format("Cannot cancel order in %s status", order.getOrderStatus())
            );
        }

        order.cancel();
        tradeOrderRepository.save(order);

        return toOrderResponse(order);
    }

    /**
     * Map TradeOrder entity to OrderResponse DTO (without fill details)
     */
    public OrderResponse toOrderResponse(TradeOrder order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setInstrumentId(order.getInstrument().getInstrumentId());
        response.setInstrumentSymbol(order.getInstrument().getSymbol());
        response.setInstrumentName(order.getInstrument().getName());
        response.setOrderAction(order.getOrderAction().name());
        response.setOrderType(order.getOrderType().name());
        response.setOrderStatus(order.getOrderStatus().name());
        response.setQuantity(order.getQuantity());
        response.setLimitPrice(order.getLimitPriceUsd());
        response.setReferencePrice(order.getReferencePriceUsd());
        response.setSubmittedAt(order.getSubmittedAt());
        response.setAcceptedAt(order.getAcceptedAt());
        response.setCancelledAt(order.getCancelledAt());
        response.setFilledAt(order.getFilledAt());
        response.setRejectedAt(order.getRejectedAt());
        response.setRejectionReason(order.getRejectionReason());
        response.setTimeInForce(order.getTimeInForce().name());
        return response;
    }
}
