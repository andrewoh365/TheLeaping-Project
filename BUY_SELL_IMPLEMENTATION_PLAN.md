# Buy/Sell Feature Implementation Plan
**Branch:** `feature/50-Buy_Sell`  
**Priority:** Must - Size 8  
**Status:** Planning Phase

---

## 🎯 Feature Overview

Implement core trading functionality that allows customers to:
- Place buy/sell orders (MARKET and LIMIT orders)
- Execute orders with current market prices
- Update holdings and cash balance
- Maintain full trade audit trail

---

## 📋 Implementation Phases

### Phase 1: Data Infrastructure (5 hours)
- [ ] Create market price management (hardcoded)
- [ ] Create data seeder for test data
- [ ] Seed initial customers, instruments, and mock holdings

### Phase 2: Trade Order Management (8 hours)
- [ ] Create repositories for trade operations
- [ ] Build TradeOrderService (order validation, placement)
- [ ] Build TradeService (order execution, settlement)
- [ ] Create DTOs for requests/responses
- [ ] Build TradeOrderController (API endpoints)

### Phase 3: Frontend Buy/Sell UI (6 hours)
- [ ] Create order placement form component
- [ ] Create order confirmation component
- [ ] Create order history/status component
- [ ] Integrate with backend API

### Phase 4: Testing & Refinement (4 hours)
- [ ] Unit tests for services
- [ ] Integration tests for workflows
- [ ] Manual testing and bug fixes

---

## 🔧 Detailed File Structure & Implementation

### Phase 1: Data Infrastructure

#### 1. Market Prices Service
**File:** `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/market/service/MarketPriceService.java`

```java
// Hardcoded current market prices (as of Sept 28, 2026)
// Can be extended to fetch from real API later
@Service
public class MarketPriceService {
    
    private static final Map<String, BigDecimal> MOCK_PRICES = Map.ofEntries(
        // US Stocks
        Map.entry("AAPL", new BigDecimal("222.50")),
        Map.entry("MSFT", new BigDecimal("431.25")),
        Map.entry("GOOGL", new BigDecimal("192.40")),
        Map.entry("TSLA", new BigDecimal("242.75")),
        Map.entry("AMZN", new BigDecimal("190.65")),
        Map.entry("NVDA", new BigDecimal("875.33")),
        Map.entry("META", new BigDecimal("555.45")),
        Map.entry("NFLX", new BigDecimal("312.10")),
        
        // Crypto (in USD)
        Map.entry("BTC", new BigDecimal("67500.00")),
        Map.entry("ETH", new BigDecimal("2650.75")),
        Map.entry("ADA", new BigDecimal("1.25")),
        Map.entry("DOGE", new BigDecimal("0.35")),
        
        // Forex (pairs, normalized to USD)
        Map.entry("EURUSD", new BigDecimal("1.08")),
        Map.entry("GBPUSD", new BigDecimal("1.27")),
        Map.entry("JPYUSD", new BigDecimal("0.0093"))
    );
    
    public BigDecimal getCurrentPrice(String symbol) {
        return MOCK_PRICES.getOrDefault(symbol, BigDecimal.ZERO);
    }
}
```

#### 2. Data Seeder (Flyway Migration or CommandLineRunner)
**File:** `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/config/DataSeeder.java`

**Purpose:** Create test data with:
- 2-3 test customers
- Mock holdings (AAPL: 100 shares, MSFT: 50 shares, BTC: 0.5, etc.)
- Initial cash balances ($10,000 USD)
- Some existing trades for audit trail

**Approach:** Implement as `CommandLineRunner` bean that runs on startup (dev profile only)

**Key SQL to seed:**
```sql
-- Test customer 1
INSERT INTO users VALUES (1, 'John', 'Doe', 'john@example.com', [hash], 'CUSTOMER', 'ACTIVE', ...);
INSERT INTO customers VALUES (1, '1990-01-15', '123-45-6789');
INSERT INTO portfolios VALUES (1, 1, 10000.00, ...);

-- Mock holdings for customer 1
INSERT INTO holdings VALUES (1, 1, 1, 100, 195.50, ...);      -- 100 AAPL @ $195.50
INSERT INTO holdings VALUES (2, 1, 2, 50, 400.00, ...);        -- 50 MSFT @ $400
INSERT INTO holdings VALUES (3, 1, 11, 0.5, 50000.00, ...);    -- 0.5 BTC @ $50k
```

---

### Phase 2: Trade Order Management

#### 1. Repository Interfaces
**Files:**
- `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/repository/TradeOrderRepository.java`
- `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/repository/TradeRepository.java`
- `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/repository/ExecutionAttemptRepository.java`
- `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/portfolio/repository/HoldingRepository.java`
- `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/portfolio/repository/PortfolioRepository.java`
- `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/portfolio/repository/CashTransactionRepository.java`

**Key Query Methods:**
```java
// TradeOrderRepository
List<TradeOrder> findByPortfolioAndOrderStatus(Portfolio, OrderStatus);
Optional<TradeOrder> findByOrderId(Long orderId);

// TradeRepository
Optional<Trade> findByOrderId(Long orderId);

// HoldingRepository
Optional<Holding> findByPortfolioAndInstrument(Portfolio, Instrument);
List<Holding> findByPortfolio(Portfolio);

// PortfolioRepository
Optional<Portfolio> findByCustomerId(Long customerId);
```

#### 2. DTOs
**File:** `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/dto/`

```java
// PlaceOrderRequest.java
@Data
public class PlaceOrderRequest {
    private Long instrumentId;
    private String orderAction;        // BUY or SELL
    private String orderType;          // MARKET or LIMIT
    private BigDecimal quantity;
    private BigDecimal limitPrice;     // null for MARKET orders
    private String timeInForce;        // DAY or GTC
}

// OrderResponse.java
@Data
public class OrderResponse {
    private Long orderId;
    private Long instrumentId;
    private String instrumentSymbol;
    private String orderAction;
    private String orderType;
    private String orderStatus;
    private BigDecimal quantity;
    private BigDecimal limitPrice;
    private OffsetDateTime submittedAt;
    private OffsetDateTime filledAt;
    private String rejectionReason;
}

// TradeConfirmationResponse.java
@Data
public class TradeConfirmationResponse {
    private Long tradeId;
    private Long orderId;
    private String instrumentSymbol;
    private BigDecimal executedQuantity;
    private BigDecimal executionPrice;
    private BigDecimal totalUsdValue;
    private OffsetDateTime executedAt;
}
```

#### 3. TradeOrderService
**File:** `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/service/TradeOrderService.java`

**Responsibilities:**
- Validate order (quantity > 0, price valid, cash sufficient for buy orders)
- Create TradeOrder record with status=SUBMITTED
- Enqueue for execution or execute immediately for MARKET orders
- Handle rejection scenarios

```java
@Service
public class TradeOrderService {
    
    @Autowired
    private TradeOrderRepository tradeOrderRepository;
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private InstrumentRepository instrumentRepository;
    
    @Autowired
    private MarketPriceService priceService;
    
    public TradeOrder placeOrder(Long customerId, PlaceOrderRequest request) {
        // 1. Load portfolio for customer
        Portfolio portfolio = portfolioRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
        
        // 2. Load instrument
        Instrument instrument = instrumentRepository.findById(request.getInstrumentId())
            .orElseThrow(() -> new ResourceNotFoundException("Instrument not found"));
        
        // 3. Validate order
        validateOrder(request, portfolio, instrument);
        
        // 4. Create and persist order
        TradeOrder order = new TradeOrder(
            portfolio,
            instrument,
            OrderAction.valueOf(request.getOrderAction()),
            OrderType.valueOf(request.getOrderType()),
            TimeInForce.valueOf(request.getTimeInForce()),
            request.getQuantity(),
            request.getLimitPrice()
        );
        
        return tradeOrderRepository.save(order);
    }
    
    private void validateOrder(PlaceOrderRequest request, Portfolio portfolio, Instrument instrument) {
        // Quantity validation
        if (request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Quantity must be positive");
        }
        
        // Limit price validation
        if ("LIMIT".equals(request.getOrderType()) && request.getLimitPrice() == null) {
            throw new ValidationException("Limit price required for LIMIT orders");
        }
        
        // Cash validation for BUY orders
        if ("BUY".equals(request.getOrderAction())) {
            BigDecimal price = request.getLimitPrice() != null 
                ? request.getLimitPrice() 
                : priceService.getCurrentPrice(instrument.getSymbol());
            
            BigDecimal requiredCash = price.multiply(request.getQuantity());
            
            if (portfolio.getCashBalanceUsd().compareTo(requiredCash) < 0) {
                throw new ValidationException(String.format(
                    "Insufficient cash. Required: %s, Available: %s",
                    requiredCash, portfolio.getCashBalanceUsd()
                ));
            }
        }
    }
}
```

#### 4. TradeService
**File:** `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/service/TradeService.java`

**Responsibilities:**
- Execute orders (get current price, create Trade record)
- Update holdings (create or update Holding record)
- Update cash balance
- Record cash transactions for audit trail
- Handle edge cases (insufficient quantity for SELL, etc.)

```java
@Service
@Transactional
public class TradeService {
    
    @Autowired
    private TradeRepository tradeRepository;
    
    @Autowired
    private TradeOrderRepository tradeOrderRepository;
    
    @Autowired
    private ExecutionAttemptRepository executionAttemptRepository;
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private HoldingRepository holdingRepository;
    
    @Autowired
    private CashTransactionRepository cashTransactionRepository;
    
    @Autowired
    private MarketPriceService priceService;
    
    public Trade executeTrade(Long orderId) {
        // 1. Load order
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        // 2. Validate order status
        if (!OrderStatus.SUBMITTED.equals(order.getOrderStatus())) {
            throw new ValidationException("Order cannot be executed in status: " + order.getOrderStatus());
        }
        
        // 3. Get current price
        BigDecimal currentPrice = priceService.getCurrentPrice(order.getInstrument().getSymbol());
        if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            rejectOrder(order, "No valid price available");
            throw new ValidationException("Cannot execute: no price available");
        }
        
        // 4. Create ExecutionAttempt record
        ExecutionAttempt attempt = createExecutionAttempt(order, currentPrice);
        
        // 5. Create Trade record
        Trade trade = createTrade(order, attempt, currentPrice);
        
        // 6. Update holdings
        updateHoldings(order, trade);
        
        // 7. Update cash balance
        updateCashBalance(order, trade);
        
        // 8. Update order status to FILLED
        order.setOrderStatus(OrderStatus.FILLED);
        order.setFilledAt(OffsetDateTime.now());
        tradeOrderRepository.save(order);
        
        return trade;
    }
    
    private void updateHoldings(TradeOrder order, Trade trade) {
        Portfolio portfolio = order.getPortfolio();
        Instrument instrument = order.getInstrument();
        
        Optional<Holding> existingHolding = holdingRepository.findByPortfolioAndInstrument(portfolio, instrument);
        
        if (existingHolding.isPresent()) {
            Holding holding = existingHolding.get();
            
            if (OrderAction.BUY.equals(order.getOrderAction())) {
                // Update average cost for BUY
                BigDecimal newQuantity = holding.getQuantity().add(trade.getQuantity());
                BigDecimal currentCost = holding.getAverageCostUsd().multiply(holding.getQuantity());
                BigDecimal newCost = currentCost.add(trade.getTotalUsdValue());
                BigDecimal newAverageCost = newCost.divide(newQuantity, 8, RoundingMode.HALF_UP);
                
                holding.setQuantity(newQuantity);
                holding.setAverageCostUsd(newAverageCost);
            } else {
                // SELL - reduce quantity
                BigDecimal newQuantity = holding.getQuantity().subtract(trade.getQuantity());
                if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ValidationException("Insufficient shares to sell");
                }
                holding.setQuantity(newQuantity);
                // Average cost stays the same
            }
            
            holding.setUpdatedAt(OffsetDateTime.now());
            holdingRepository.save(holding);
        } else {
            // New holding (BUY only)
            if (!OrderAction.BUY.equals(order.getOrderAction())) {
                throw new ValidationException("No existing holding to sell");
            }
            
            Holding newHolding = new Holding(
                portfolio,
                instrument,
                trade.getQuantity(),
                trade.getExecutionPriceUsd()
            );
            holdingRepository.save(newHolding);
        }
    }
    
    private void updateCashBalance(TradeOrder order, Trade trade) {
        Portfolio portfolio = order.getPortfolio();
        BigDecimal balanceBefore = portfolio.getCashBalanceUsd();
        BigDecimal transactionAmount;
        BigDecimal balanceAfter;
        
        if (OrderAction.BUY.equals(order.getOrderAction())) {
            transactionAmount = trade.getTotalUsdValue().negate();  // Negative for buy
            balanceAfter = balanceBefore.add(transactionAmount);
        } else {
            transactionAmount = trade.getTotalUsdValue();  // Positive for sell
            balanceAfter = balanceBefore.add(transactionAmount);
        }
        
        portfolio.setCashBalanceUsd(balanceAfter);
        portfolio.setUpdatedAt(OffsetDateTime.now());
        portfolioRepository.save(portfolio);
        
        // Record cash transaction
        CashTransaction transaction = new CashTransaction(
            portfolio,
            trade,
            OrderAction.BUY.equals(order.getOrderAction()) ? "TRADE_BUY" : "TRADE_SELL",
            transactionAmount,
            balanceBefore,
            balanceAfter
        );
        cashTransactionRepository.save(transaction);
    }
    
    private void rejectOrder(TradeOrder order, String reason) {
        order.setOrderStatus(OrderStatus.REJECTED);
        order.setRejectedAt(OffsetDateTime.now());
        order.setRejectionReason(reason);
        tradeOrderRepository.save(order);
    }
}
```

#### 5. TradeOrderController
**File:** `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/controller/TradeOrderController.java`

```java
@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradeOrderController {
    
    private final TradeOrderService tradeOrderService;
    private final TradeService tradeService;
    
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(
        @RequestBody PlaceOrderRequest request,
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        TradeOrder order = tradeOrderService.placeOrder(userPrincipal.getId(), request);
        
        // Execute immediately for MARKET orders
        if ("MARKET".equals(request.getOrderType())) {
            try {
                Trade trade = tradeService.executeTrade(order.getOrderId());
            } catch (Exception e) {
                // Order execution failed but order is persisted
                // Frontend will see status as REJECTED with reason
            }
        }
        
        return ResponseEntity.ok(mapToResponse(order));
    }
    
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
        @PathVariable Long orderId,
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        // Verify ownership
        if (!order.getPortfolio().getCustomer().getId().equals(userPrincipal.getId())) {
            throw new AccessDeniedException("Unauthorized access to order");
        }
        
        return ResponseEntity.ok(mapToResponse(order));
    }
    
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
        @PathVariable Long orderId,
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        // Validate ownership and status, cancel if allowed
        // Similar to getOrder but with additional business logic
        return ResponseEntity.ok(mapToResponse(cancelledOrder));
    }
    
    private OrderResponse mapToResponse(TradeOrder order) {
        return OrderResponse.builder()
            .orderId(order.getOrderId())
            .instrumentId(order.getInstrument().getInstrumentId())
            .instrumentSymbol(order.getInstrument().getSymbol())
            .orderAction(order.getOrderAction().name())
            .orderType(order.getOrderType().name())
            .orderStatus(order.getOrderStatus().name())
            .quantity(order.getQuantity())
            .limitPrice(order.getLimitPriceUsd())
            .submittedAt(order.getSubmittedAt())
            .filledAt(order.getFilledAt())
            .rejectionReason(order.getRejectionReason())
            .build();
    }
}
```

---

## 🗂️ File Checklist

### Create New Files (Phase 1)
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/market/service/MarketPriceService.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/config/DataSeeder.java`

### Create New Files (Phase 2)
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/repository/TradeOrderRepository.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/repository/TradeRepository.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/repository/ExecutionAttemptRepository.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/portfolio/repository/HoldingRepository.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/portfolio/repository/PortfolioRepository.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/portfolio/repository/CashTransactionRepository.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/dto/PlaceOrderRequest.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/dto/OrderResponse.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/dto/TradeConfirmationResponse.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/service/TradeOrderService.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/service/TradeService.java`
- [ ] `backend/portfolio-app/src/main/java/com/leaping/portfolio_app/trade/controller/TradeOrderController.java`

### Modify Existing Files
- [ ] `Portfolio.java` - Add methods to update cash balance
- [ ] `Holding.java` - Add constructor if missing
- [ ] `CashTransaction.java` - Add constructors
- [ ] `ExecutionAttempt.java` - Verify complete

---

## 🔌 Integration Points

### API Endpoints
```
POST   /api/trade/orders                    - Place new order
GET    /api/trade/orders/{orderId}          - Get order details
POST   /api/trade/orders/{orderId}/cancel   - Cancel order
GET    /api/trade/orders?status=FILLED      - List filled orders
```

### Authentication
- All endpoints require JWT token in `Authorization: Bearer <token>` header
- Use `@AuthenticationPrincipal UserPrincipal` to get current user
- Verify customer owns the portfolio before executing trades

### Error Handling
- `ValidationException` - Order validation failures (insufficient cash, invalid quantity)
- `ResourceNotFoundException` - Portfolio/Instrument/Order not found
- `AccessDeniedException` - Unauthorized access to portfolio

---

## ⏱️ Time Estimates

| Phase | Task | Hours |
|-------|------|-------|
| 1 | MarketPriceService | 1 |
| 1 | DataSeeder | 2 |
| 1 | Integration testing | 2 |
| 2 | Repositories | 1 |
| 2 | DTOs | 1 |
| 2 | TradeOrderService | 3 |
| 2 | TradeService | 3 |
| 2 | TradeOrderController | 2 |
| **Total** | | **15 hours** |

---

## 🚀 Next Steps

1. Create MarketPriceService with hardcoded prices
2. Create DataSeeder to populate test data
3. Run seeder and verify data is created
4. Build repositories (simple JpaRepository interfaces)
5. Build TradeOrderService with validation
6. Build TradeService with execution logic
7. Build TradeOrderController with REST endpoints
8. Test with Postman/API client
9. Build frontend UI components (Phase 3)

