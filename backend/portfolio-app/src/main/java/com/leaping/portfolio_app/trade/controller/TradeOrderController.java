package com.leaping.portfolio_app.trade.controller;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import com.leaping.portfolio_app.trade.dto.OrderResponse;
import com.leaping.portfolio_app.trade.dto.PlaceOrderRequest;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.enums.OrderStatus;
import com.leaping.portfolio_app.trade.service.TradeOrderService;

/**
 * TradeOrderController handles all order-related REST endpoints.
 * 
 * All endpoints are protected - require valid JWT token.
 * 
 * Endpoints:
 * - GET /api/trade/preview - Preview an order (price, total, availability)
 * - POST /api/trade/orders - Place new buy/sell order (202 once accepted; executes asynchronously)
 * - GET /api/trade/orders - List the client's orders (newest first, paged)
 * - GET /api/trade/orders/{orderId} - Get order details
 * - POST /api/trade/orders/{orderId}/cancel - Cancel an open (not yet filled) order
 */
@RestController
@RequestMapping("/api/trade")
public class TradeOrderController {
    private static final Logger log = LoggerFactory.getLogger(TradeOrderController.class);

    private final TradeOrderService tradeOrderService;
    private final UserRepository userRepository;

    public TradeOrderController(
            TradeOrderService tradeOrderService,
            UserRepository userRepository
    ) {
        this.tradeOrderService = tradeOrderService;
        this.userRepository = userRepository;
    }

    /**
     * Place a new buy or sell order.
     *
     * The order is recorded, checked against the trading rules, and either:
     * - ACCEPTED: returns 202 with the order. Execution happens asynchronously;
     *   poll GET /api/trade/orders/{orderId} for the outcome (FILLED or REJECTED).
     * - REJECTED: returns 400 with the reason and the recorded order.
     *
     * MARKET orders are executed straight after acceptance. LIMIT orders stay
     * ACCEPTED until cancelled (limit execution is a future implementation).
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

            TradeOrder order = tradeOrderService.placeOrder(userId, request);
            OrderResponse response = tradeOrderService.toOrderResponse(order);

            if (OrderStatus.REJECTED.equals(order.getOrderStatus())) {
                // Failed the trading rules; recorded as rejected and the reason is client-readable
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(order.getRejectionReason(), response));
            }
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (IllegalArgumentException e) {
            // Failed the trading rules before being accepted; message is client-readable
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage(), null));
        } catch (Exception e) {
            log.error("Failed to place order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Something went wrong placing your order. Please try again.", null));
        }
    }

    /**
     * Preview an order before submitting it: indicative price, estimated total,
     * available cash/holding, and whether the order would be accepted (BR-13).
     *
     * @param instrumentId The instrument to trade
     * @param action "BUY" or "SELL"
     * @param quantity Optional quantity; omit to just get price and availability
     * @param authentication Spring Security authentication
     * @return OrderPreviewResponse
     */
    @GetMapping("/preview")
    public ResponseEntity<?> previewOrder(
        @RequestParam Long instrumentId,
        @RequestParam(defaultValue = "BUY") String action,
        @RequestParam(required = false) BigDecimal quantity,
        Authentication authentication
    ) {
        try {
            Long userId = resolveCurrentUserId(authentication);
            return ResponseEntity.ok(tradeOrderService.previewOrder(userId, instrumentId, action, quantity));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage(), null));
        }
    }

    /**
     * List the signed-in client's orders, newest first (BR-11).
     * 
     * @param page Zero-based page number (default 0)
     * @param size Page size (default 20, max 100)
     * @param authentication Spring Security authentication
     * @return OrderHistoryResponse with the page of orders and whether more exist
     */
    @GetMapping("/orders")
    public ResponseEntity<?> getOrderHistory(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Authentication authentication
    ) {
        Long userId = resolveCurrentUserId(authentication);
        return ResponseEntity.ok(tradeOrderService.getOrderHistory(userId, page, size));
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
     * Cancel an open order.
     * 
     * Only cancels orders that are still open (SUBMITTED or ACCEPTED).
     * Once an order is FILLED, REJECTED or CANCELLED, it cannot be cancelled.
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

    private Long resolveCurrentUserId(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
        return user.getId();
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
