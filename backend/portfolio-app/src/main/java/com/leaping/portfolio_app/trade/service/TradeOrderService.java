package com.leaping.portfolio_app.trade.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
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

    public TradeOrderService(
            TradeOrderRepository tradeOrderRepository,
            PortfolioRepository portfolioRepository,
            InstrumentRepository instrumentRepository,
            HoldingRepository holdingRepository,
            MarketPriceService priceService
    ) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.portfolioRepository = portfolioRepository;
        this.instrumentRepository = instrumentRepository;
        this.holdingRepository = holdingRepository;
        this.priceService = priceService;
    }

    /**
     * Place a new order on behalf of a customer.
     * 
     * Validates order before creation. If validation fails, throws exception.
     * Creates order in SUBMITTED status and returns it.
     * 
     * @param customerId The customer placing the order (identified by User ID)
     * @param request The order request with symbol, quantity, and price
     * @return The created TradeOrder entity
     * @throws IllegalArgumentException if validation fails
     */
    public TradeOrder placeOrder(Long customerId, PlaceOrderRequest request) {
        // 1. Load portfolio for customer
        Portfolio portfolio = portfolioRepository.findByCustomerUserId(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found for customer"));

        // 2. Load instrument
        Instrument instrument = instrumentRepository.findById(request.getInstrumentId())
            .orElseThrow(() -> new IllegalArgumentException("Instrument not found"));

        // 3. Validate order
        validateOrder(request, portfolio, instrument);

        // 4. Create and persist order
        TradeOrder order = new TradeOrder(
            portfolio,
            instrument,
            OrderAction.valueOf(request.getOrderAction().toUpperCase()),
            OrderType.valueOf(request.getOrderType().toUpperCase()),
            TimeInForce.valueOf(request.getTimeInForce() != null ? request.getTimeInForce().toUpperCase() : "DAY"),
            request.getQuantity(),
            request.getLimitPrice()
        );

        return tradeOrderRepository.save(order);
    }

    /**
     * Validate an order request for basic rule violations.
     * 
     * Checks:
     * - Quantity is positive
     * - For LIMIT orders: limit price is specified and positive
     * - For BUY orders: customer has sufficient cash
     * 
     * @param request The order request to validate
     * @param portfolio The customer's portfolio
     * @param instrument The instrument being traded
     * @throws IllegalArgumentException if any validation fails
     */
    private void validateOrder(PlaceOrderRequest request, Portfolio portfolio, Instrument instrument) {
        // Quantity validation
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be a positive number");
        }

        if (request.getOrderAction() == null || request.getOrderType() == null) {
            throw new IllegalArgumentException("Order action and order type are required");
        }

        // Order type and limit price validation
        OrderType orderType = OrderType.valueOf(request.getOrderType().toUpperCase());
        if (OrderType.LIMIT.equals(orderType)) {
            if (request.getLimitPrice() == null) {
                throw new IllegalArgumentException("Limit price is required for LIMIT orders");
            }
            if (request.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Limit price must be positive");
            }
        }

        // Cash validation for BUY orders
        OrderAction action = OrderAction.valueOf(request.getOrderAction().toUpperCase());
        if (OrderAction.BUY.equals(action)) {
            // Get price: use limit price if specified, otherwise current market price
            BigDecimal price = request.getLimitPrice() != null 
                ? request.getLimitPrice() 
                : priceService.getCurrentPrice(instrument.getSymbol());

            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Cannot determine price for this instrument");
            }

            // Calculate required cash
            BigDecimal requiredCash = price.multiply(request.getQuantity());

            // Check if customer has sufficient cash
            if (portfolio.getCashBalanceUsd().compareTo(requiredCash) < 0) {
                throw new IllegalArgumentException(
                    String.format(
                        "Insufficient cash. Required: $%.2f, Available: $%.2f",
                        requiredCash,
                        portfolio.getCashBalanceUsd()
                    )
                );
            }
        } else {
            Holding holding = holdingRepository
                .findByPortfolioAndInstrument(portfolio, instrument)
                .orElseThrow(() -> new IllegalArgumentException("Cannot sell an instrument that is not owned"));

            if (holding.getQuantity().compareTo(request.getQuantity()) < 0) {
                throw new IllegalArgumentException(
                    String.format(
                        "Insufficient shares to sell. Owned: %s, Requested: %s",
                        holding.getQuantity().toPlainString(),
                        request.getQuantity().toPlainString()
                    )
                );
            }
        }
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

        return mapToOrderResponse(order);
    }

    /**
     * Cancel a pending order.
     * Only allows cancellation of SUBMITTED orders.
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

        // Validate order can be cancelled
        if (!OrderStatus.SUBMITTED.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException(
                String.format("Cannot cancel order in %s status", order.getOrderStatus())
            );
        }

        // Cancel the order
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(OffsetDateTime.now());
        tradeOrderRepository.save(order);

        return mapToOrderResponse(order);
    }

    /**
     * Map TradeOrder entity to OrderResponse DTO
     */
    private OrderResponse mapToOrderResponse(TradeOrder order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setInstrumentId(order.getInstrument().getInstrumentId());
        response.setInstrumentSymbol(order.getInstrument().getSymbol());
        response.setOrderAction(order.getOrderAction().name());
        response.setOrderType(order.getOrderType().name());
        response.setOrderStatus(order.getOrderStatus().name());
        response.setQuantity(order.getQuantity());
        response.setLimitPrice(order.getLimitPriceUsd());
        response.setSubmittedAt(order.getSubmittedAt());
        response.setFilledAt(order.getFilledAt());
        response.setRejectedAt(order.getRejectedAt());
        response.setRejectionReason(order.getRejectionReason());
        response.setTimeInForce(order.getTimeInForce().name());
        return response;
    }
}
