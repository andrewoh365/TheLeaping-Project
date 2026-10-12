package com.leaping.portfolio_app.trade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import com.leaping.portfolio_app.trade.dto.OrderResponse;
import com.leaping.portfolio_app.trade.dto.PlaceOrderRequest;
import com.leaping.portfolio_app.trade.dto.TradeConfirmationResponse;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.service.TradeOrderService;
import com.leaping.portfolio_app.trade.service.TradeService;

/**
 * TradeOrderController handles all order-related REST endpoints.
 * 
 * All endpoints are protected - require valid JWT token.
 * 
 * Endpoints:
 * - POST /api/trade/orders - Place new buy/sell order
 * - GET /api/trade/orders/{orderId} - Get order details
 * - POST /api/trade/orders/{orderId}/cancel - Cancel pending order
 */
@RestController
@RequestMapping("/api/trade")
public class TradeOrderController {
    private final TradeOrderService tradeOrderService;
    private final TradeService tradeService;
    private final UserRepository userRepository;

    public TradeOrderController(
            TradeOrderService tradeOrderService,
            TradeService tradeService,
            UserRepository userRepository
    ) {
        this.tradeOrderService = tradeOrderService;
        this.tradeService = tradeService;
        this.userRepository = userRepository;
    }

    /**
     * Place a new buy or sell order.
     * 
     * For MARKET orders: Executes immediately with current market price
     * For LIMIT orders: Waits for price to reach limit (future implementation)
     * 
     * @param request Order details (instrument, quantity, type, price)
     * @param authentication Spring Security authentication (contains user info)
     * @return OrderResponse with order details and status
     */
    @PostMapping("/orders")
    public ResponseEntity<?> placeOrder(
        @RequestBody PlaceOrderRequest request,
        Authentication authentication
    ) {
        try {
            Long userId = resolveCurrentUserId(authentication);

            // 1. Create order in SUBMITTED status
            TradeOrder order = tradeOrderService.placeOrder(userId, request);

            // 2. For MARKET orders, execute immediately
            if ("MARKET".equalsIgnoreCase(request.getOrderType())) {
                try {
                    TradeConfirmationResponse tradeConfirmation = tradeService.executeTrade(order.getOrderId());
                    OrderResponse updatedOrder = tradeOrderService.getOrder(order.getOrderId(), userId);
                    
                    // Return both order and trade confirmation
                    return ResponseEntity.status(HttpStatus.CREATED)
                        .body(new OrderExecutedResponse(updatedOrder, tradeConfirmation));
                        
                } catch (Exception e) {
                    OrderResponse updatedOrder = tradeOrderService.getOrder(order.getOrderId(), userId);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Trade execution failed: " + e.getMessage(), updatedOrder));
                }
            }

            // For LIMIT orders, return order waiting for execution (future implementation)
            return ResponseEntity.status(HttpStatus.CREATED).body(mapToOrderResponse(order));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Invalid order: " + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to place order: " + e.getMessage(), null));
        }
    }

    /**
     * Get order details by order ID.
     * 
     * @param orderId The ID of the order to retrieve
     * @param authentication Spring Security authentication
     * @return OrderResponse with current order status
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(
        @PathVariable Long orderId,
        Authentication authentication
    ) {
        try {
            Long userId = resolveCurrentUserId(authentication);

            OrderResponse order = tradeOrderService.getOrder(orderId, userId);
            return ResponseEntity.ok(order);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("Access denied or order not found: " + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to retrieve order: " + e.getMessage(), null));
        }
    }

    /**
     * Cancel a pending order.
     * 
     * Only cancels orders in SUBMITTED status.
     * Once an order is FILLED, it cannot be cancelled.
     * 
     * @param orderId The ID of the order to cancel
     * @param authentication Spring Security authentication
     * @return Updated OrderResponse with status CANCELLED
     */
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
        @PathVariable Long orderId,
        Authentication authentication
    ) {
        try {
            Long userId = resolveCurrentUserId(authentication);

            OrderResponse cancelledOrder = tradeOrderService.cancelOrder(orderId, userId);
            return ResponseEntity.ok(cancelledOrder);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Cannot cancel order: " + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to cancel order: " + e.getMessage(), null));
        }
    }

    /**
     * Map TradeOrder to OrderResponse
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

    private Long resolveCurrentUserId(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
        return user.getId();
    }

    /**
     * Response wrapper for successful order execution
     */
    public static class OrderExecutedResponse {
        public OrderResponse order;
        public TradeConfirmationResponse trade;

        public OrderExecutedResponse(OrderResponse order, TradeConfirmationResponse trade) {
            this.order = order;
            this.trade = trade;
        }

        public OrderResponse getOrder() {
            return order;
        }

        public TradeConfirmationResponse getTrade() {
            return trade;
        }
    }

    /**
     * Error response wrapper
     */
    public static class ErrorResponse {
        public String message;
        public OrderResponse order;

        public ErrorResponse(String message, OrderResponse order) {
            this.message = message;
            this.order = order;
        }

        public String getMessage() {
            return message;
        }

        public OrderResponse getOrder() {
            return order;
        }
    }
}
