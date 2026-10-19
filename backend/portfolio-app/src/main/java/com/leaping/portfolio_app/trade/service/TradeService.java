package com.leaping.portfolio_app.trade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.leaping.portfolio_app.trade.exception.TradeRejectedException;
import com.leaping.portfolio_app.trade.repository.ExecutionAttemptRepository;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

/**
 * TradeService handles trade execution and settlement.
 * 
 * Responsibilities:
 * - Execute orders with current market prices
 * - Create Trade and ExecutionAttempt records
 * - Update customer holdings
 * - Update cash balance
 * - Record transaction audit trail
 * - Handle error cases (insufficient shares, price unavailable, etc.)
 */
@Service
@Transactional
public class TradeService {
    private final TradeRepository tradeRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final ExecutionAttemptRepository executionAttemptRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final PricingService pricingService;
    private final EntityManager entityManager;
    private final AuditService auditService;

    public TradeService(
            TradeRepository tradeRepository,
            TradeOrderRepository tradeOrderRepository,
            ExecutionAttemptRepository executionAttemptRepository,
            PortfolioRepository portfolioRepository,
            HoldingRepository holdingRepository,
            CashTransactionRepository cashTransactionRepository,
            PricingService pricingService,
            EntityManager entityManager,
            AuditService auditService
    ) {
        this.tradeRepository = tradeRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.executionAttemptRepository = executionAttemptRepository;
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.pricingService = pricingService;
        this.entityManager = entityManager;
        this.auditService = auditService;
    }

    /**
     * Execute a submitted order.
     *
     * Workflow:
     * 1. Lock the order and validate it is in SUBMITTED status
     * 2. Lock the portfolio so concurrent orders on it are serialised
    * 3. Capture the native quote and USD conversion snapshot
    * 4. Re-check cash (BUY) or holding (SELL) against the USD execution value
    * 5. Create the ExecutionAttempt and Trade records
     * 6. Update holdings and cash balance
     * 7. Mark order as FILLED
     *
    * Rejections persist failed execution-attempt evidence, pricing evidence, and
    * the REJECTED order status before throwing TradeRejectedException. That
    * exception does not roll back, so the rejection remains durable without any
    * successful trade, holding, or cash settlement. Any other failure rolls back
    * the entire execution transaction and leaves the order SUBMITTED.
     *
     * @param orderId The order to execute
     * @return Trade confirmation details
     * @throws TradeRejectedException if the order was rejected (and recorded as such)
     * @throws IllegalArgumentException if the order cannot be executed
     */
    @Transactional(noRollbackFor = TradeRejectedException.class)
    public TradeConfirmationResponse executeTrade(Long orderId) {
        // 1. Load and lock order.
        // refresh(..., PESSIMISTIC_WRITE) re-reads the row with SELECT ... FOR UPDATE. A plain
        // locking query is not enough: with open-session-in-view the entity may already be cached
        // from order placement, and Hibernate would hand back that stale copy after acquiring the lock.
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (!OrderStatus.SUBMITTED.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException(
                String.format("Order cannot be executed in %s status", order.getOrderStatus())
            );
        }

        // 2. Lock portfolio (held until commit). Every cash/holding change for this portfolio
        // goes through this lock, so concurrent orders are serialised.
        Portfolio portfolio = order.getPortfolio();
        entityManager.refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

        Instrument instrument = order.getInstrument();

        // 3. Capture the persisted native quote and its conversion inputs together.
        QuoteSnapshot quote = captureQuote(instrument);
        if (!quote.isValid()) {
            String reason = String.format(
                "We don't have a current price for %s, so the order couldn't be filled.",
                instrument.getSymbol()
            );
            ExecutionAttempt failedAttempt = createExecutionAttempt(
                order,
                quote,
                ExecutionAttemptStatus.FAILED,
                reason
            );
            recordPriceAudit(
                AuditEventType.PRICE_CAPTURE_FAILED,
                order,
                failedAttempt,
                quote,
                reason
            );
            throw reject(order, reason, failedAttempt);
        }

        // 4. Re-check funds under the lock, at the actual execution price
        BigDecimal totalUsdValue = quote.usdPrice()
            .multiply(order.getQuantity())
            .setScale(2, RoundingMode.HALF_UP);
        if (totalUsdValue.compareTo(BigDecimal.ZERO) == 0) {
            String reason = "This order's settlement value rounds to $0.00, so it couldn't be filled.";
            ExecutionAttempt failedAttempt = createExecutionAttempt(
                order,
                quote,
                ExecutionAttemptStatus.FAILED,
                reason
            );
            recordPriceAudit(
                AuditEventType.PRICE_CAPTURED,
                order,
                failedAttempt,
                quote,
                reason
            );
            throw reject(order, reason, failedAttempt);
        }
        if (OrderAction.BUY.equals(order.getOrderAction())) {
            if (portfolio.getCashBalanceUsd().compareTo(totalUsdValue) < 0) {
                String reason = String.format(
                    "Not enough cash. This order costs $%,.2f and you have $%,.2f available.",
                    totalUsdValue, portfolio.getCashBalanceUsd()
                );
                ExecutionAttempt failedAttempt = createExecutionAttempt(
                    order,
                    quote,
                    ExecutionAttemptStatus.FAILED,
                    reason
                );
                recordPriceAudit(
                    AuditEventType.PRICE_CAPTURED,
                    order,
                    failedAttempt,
                    quote,
                    reason
                );
                throw reject(order, reason, failedAttempt);
            }
        } else {
            Optional<Holding> holding = holdingRepository
                .findByPortfolio_PortfolioIdAndInstrument_InstrumentId(
                    portfolio.getPortfolioId(), instrument.getInstrumentId());
            // Re-read under the portfolio lock in case a stale copy is cached.
            holding.ifPresent(entityManager::refresh);
            BigDecimal owned = holding.map(Holding::getQuantity).orElse(BigDecimal.ZERO);
            if (owned.compareTo(order.getQuantity()) < 0) {
                String reason = owned.compareTo(BigDecimal.ZERO) <= 0
                    ? String.format("You don't own any %s to sell.", instrument.getSymbol())
                    : String.format("You only own %s %s.",
                        owned.stripTrailingZeros().toPlainString(), instrument.getSymbol());
                ExecutionAttempt failedAttempt = createExecutionAttempt(
                    order,
                    quote,
                    ExecutionAttemptStatus.FAILED,
                    reason
                );
                recordPriceAudit(
                    AuditEventType.PRICE_CAPTURED,
                    order,
                    failedAttempt,
                    quote,
                    reason
                );
                throw reject(order, reason, failedAttempt);
            }
        }

        // 5. Create the execution attempt and trade records.
        ExecutionAttempt attempt = createExecutionAttempt(
            order,
            quote,
            ExecutionAttemptStatus.FILLED,
            null
        );
        recordPriceAudit(
            AuditEventType.PRICE_CAPTURED,
            order,
            attempt,
            quote,
            null
        );
        Trade trade = createTrade(order, attempt, quote, totalUsdValue);

        // 6. Update holdings and cash
        updateHoldings(order, trade);
        updateCashBalance(portfolio, order, trade);

        // 7. Update order status to FILLED
        order.setOrderStatus(OrderStatus.FILLED);
        order.setFilledAt(OffsetDateTime.now());
        TradeOrder savedOrder = tradeOrderRepository.save(order);

        Map<String, Object> tradeDetails = new LinkedHashMap<>();
        tradeDetails.put("orderId", savedOrder.getOrderId());
        tradeDetails.put("executionAttemptId", attempt.getExecutionAttemptId());
        tradeDetails.put("tradeId", trade.getTradeId());
        tradeDetails.put("quantity", trade.getQuantity());
        tradeDetails.put("executionPrice", trade.getExecutionPrice());
        tradeDetails.put(
            "executionPriceCurrencyCode",
            trade.getExecutionPriceCurrency().getCurrencyCode()
        );
        tradeDetails.put("exchangeRateToUsd", trade.getExchangeRateToUsdAtExecution());
        tradeDetails.put("executionPriceUsd", trade.getExecutionPriceUsd());
        tradeDetails.put("totalUsdValue", trade.getTotalUsdValue());
        tradeDetails.put("orderStatusBefore", OrderStatus.SUBMITTED.name());
        tradeDetails.put("orderStatusAfter", savedOrder.getOrderStatus().name());
        tradeDetails.put("filledAt", savedOrder.getFilledAt());

        auditService.recordEvent(
            AuditEventType.TRADE_FILLED,
            portfolio.getCustomer().getUser(),
            savedOrder,
            attempt,
            trade,
            null,
            tradeDetails
        );

        return mapToTradeConfirmation(trade, savedOrder);
    }

    /**
     * Create ExecutionAttempt record for audit trail.
     */
    private ExecutionAttempt createExecutionAttempt(
            TradeOrder order,
            QuoteSnapshot quote,
            ExecutionAttemptStatus status,
            String reason
    ) {
        ExecutionAttempt attempt = new ExecutionAttempt();
        attempt.setOrder(order);
        attempt.setAttemptNumber(1);
        attempt.setQuotedPrice(quote.nativePrice());
        attempt.setPriceCurrency(quote.currency());
        attempt.setExchangeRateToUsd(quote.exchangeRateToUsd());
        attempt.setQuotedPriceUsd(quote.usdPrice());
        attempt.setQuoteTimestamp(quote.quoteTimestamp());
        attempt.setSourceType(quote.sourceType());
        attempt.setProviderName(quote.providerName());
        attempt.setAttemptStatus(status);
        attempt.setReason(reason);
        attempt.setAttemptedAt(OffsetDateTime.now());

        return executionAttemptRepository.save(attempt);
    }

    /**
     * Create Trade record when order is filled.
     */
    private Trade createTrade(
            TradeOrder order,
            ExecutionAttempt attempt,
            QuoteSnapshot quote,
            BigDecimal totalUsdValue
    ) {
        Trade trade = new Trade();
        trade.setOrder(order);
        trade.setExecutionAttempt(attempt);
        trade.setQuantity(order.getQuantity());
        trade.setExecutionPrice(quote.nativePrice());
        trade.setExecutionPriceCurrency(quote.currency());
        trade.setExchangeRateToUsdAtExecution(quote.exchangeRateToUsd());
        
        trade.setExecutionPriceUsd(quote.usdPrice());
        trade.setTotalUsdValue(totalUsdValue);
        trade.setExecutedAt(OffsetDateTime.now());

        return tradeRepository.save(trade);
    }

    /**
     * Update holdings after trade execution.
     * 
     * For BUY:
     * - If holding doesn't exist, create new holding
     * - If holding exists, update quantity and recalculate average cost
     * 
     * For SELL:
     * - Reduce quantity
     * - Keep average cost unchanged
     * - Throw error if insufficient shares
     */
    private void updateHoldings(TradeOrder order, Trade trade) {
        Portfolio portfolio = order.getPortfolio();
        Instrument instrument = order.getInstrument();

        Optional<Holding> existingHolding = holdingRepository.findByPortfolio_PortfolioIdAndInstrument_InstrumentId(
            portfolio.getPortfolioId(), instrument.getInstrumentId());

        if (existingHolding.isPresent()) {
            Holding holding = existingHolding.get();
            BigDecimal quantityBefore = holding.getQuantity();
            BigDecimal averageCostBefore = holding.getAverageCostUsd();

            if (OrderAction.BUY.equals(order.getOrderAction())) {
                // BUY: Update quantity and recalculate average cost
                BigDecimal newQuantity = holding.getQuantity().add(trade.getQuantity());
                
                // Recalculate average cost: (old_quantity * old_cost + new_quantity * new_cost) / total_quantity
                BigDecimal currentCostBasis = holding.getAverageCostUsd().multiply(holding.getQuantity());
                BigDecimal newCostBasis = currentCostBasis.add(trade.getTotalUsdValue());
                BigDecimal newAverageCost = newCostBasis.divide(newQuantity, 8, RoundingMode.HALF_UP);

                holding.setQuantity(newQuantity);
                holding.setAverageCostUsd(newAverageCost);
                
            } else {
                // SELL: Reduce quantity
                BigDecimal newQuantity = holding.getQuantity().subtract(trade.getQuantity());
                
                if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Insufficient shares to sell. Owned: %f, Requested: %f",
                            holding.getQuantity(),
                            trade.getQuantity()
                        )
                    );
                }
                
                if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    // Position fully closed: remove the holding
                    holdingRepository.delete(holding);
                    recordHoldingAudit(
                        order,
                        trade,
                        holding.getHoldingId(),
                        quantityBefore,
                        BigDecimal.ZERO,
                        averageCostBefore,
                        null
                    );
                    return;
                }

                holding.setQuantity(newQuantity);
                // Average cost stays the same
            }

            holding.setUpdatedAt(OffsetDateTime.now());
            Holding savedHolding = holdingRepository.save(holding);
            recordHoldingAudit(
                order,
                trade,
                savedHolding.getHoldingId(),
                quantityBefore,
                savedHolding.getQuantity(),
                averageCostBefore,
                savedHolding.getAverageCostUsd()
            );

        } else {
            // New holding (only valid for BUY)
            if (!OrderAction.BUY.equals(order.getOrderAction())) {
                throw new IllegalArgumentException("Cannot sell - no existing holding for this instrument");
            }

            Holding newHolding = new Holding();
            newHolding.setPortfolio(portfolio);
            newHolding.setInstrument(instrument);
            newHolding.setQuantity(trade.getQuantity());
            // The eight-decimal stored cost can differ from the settlement total by a tiny
            // amount when it is multiplied by a fractional quantity.
            newHolding.setAverageCostUsd(
                trade.getTotalUsdValue()
                    .divide(trade.getQuantity(), 8, RoundingMode.HALF_UP)
            );
            newHolding.setUpdatedAt(OffsetDateTime.now());

            Holding savedHolding = holdingRepository.save(newHolding);
            recordHoldingAudit(
                order,
                trade,
                savedHolding.getHoldingId(),
                BigDecimal.ZERO,
                savedHolding.getQuantity(),
                BigDecimal.ZERO,
                savedHolding.getAverageCostUsd()
            );
        }
    }

    /**
     * Update cash balance after trade execution.
     * 
     * BUY: Reduces cash balance (negative transaction)
     * SELL: Increases cash balance (positive transaction)
     * 
     * Records transaction for audit trail.
     */
    private CashTransaction updateCashBalance(
            Portfolio portfolio,
            TradeOrder order,
            Trade trade
    ) {
        BigDecimal balanceBefore = portfolio.getCashBalanceUsd()
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal transactionAmount;
        BigDecimal balanceAfter;
        BigDecimal cashValue = trade.getTotalUsdValue()
            .setScale(2, RoundingMode.UNNECESSARY);

        if (OrderAction.BUY.equals(order.getOrderAction())) {
            // BUY: Negative impact on cash (spending money)
            transactionAmount = cashValue.negate();
            balanceAfter = balanceBefore.subtract(cashValue);
            
        } else {
            // SELL: Positive impact on cash (receiving money)
            transactionAmount = cashValue;
            balanceAfter = balanceBefore.add(cashValue);
        }

        portfolio.setCashBalanceUsd(balanceAfter);
        portfolio.setUpdatedAt(OffsetDateTime.now());
        portfolioRepository.save(portfolio);

        // Record transaction for audit
        CashTransaction transaction = new CashTransaction();
        transaction.setPortfolio(portfolio);
        transaction.setTrade(trade);
        transaction.setTransactionType(
            OrderAction.BUY.equals(order.getOrderAction()) 
                ? CashTransactionType.TRADE_BUY 
                : CashTransactionType.TRADE_SELL
        );
        transaction.setAmountUsd(transactionAmount);
        transaction.setBalanceBeforeUsd(balanceBefore);
        transaction.setBalanceAfterUsd(balanceAfter);
        // createdAt is set by @PrePersist

        CashTransaction savedTransaction = cashTransactionRepository.save(transaction);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("cashTransactionId", savedTransaction.getCashTransactionId());
        details.put("portfolioId", portfolio.getPortfolioId());
        details.put("transactionType", savedTransaction.getTransactionType().name());
        details.put("amountUsd", savedTransaction.getAmountUsd());
        details.put("balanceBeforeUsd", savedTransaction.getBalanceBeforeUsd());
        details.put("balanceAfterUsd", savedTransaction.getBalanceAfterUsd());
        details.put("orderId", order.getOrderId());
        details.put("tradeId", trade.getTradeId());
        details.put("executionAttemptId", trade.getExecutionAttempt().getExecutionAttemptId());

        auditService.recordEvent(
            AuditEventType.CASH_BALANCE_CHANGED,
            portfolio.getCustomer().getUser(),
            order,
            trade.getExecutionAttempt(),
            trade,
            savedTransaction,
            details
        );

        return savedTransaction;
    }

    /**
     * Mark an order REJECTED with a reason and return the exception to throw.
     * The caller must throw it so the transaction commits the rejection.
     */
    private TradeRejectedException reject(
            TradeOrder order,
            String reason,
            ExecutionAttempt attempt
    ) {
        order.setOrderStatus(OrderStatus.REJECTED);
        order.setRejectedAt(OffsetDateTime.now());
        order.setRejectionReason(reason);
        TradeOrder savedOrder = tradeOrderRepository.save(order);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderId", savedOrder.getOrderId());
        details.put("executionAttemptId", attempt.getExecutionAttemptId());
        details.put("reason", reason);
        details.put("rejectedAt", savedOrder.getRejectedAt());
        details.put("previousStatus", OrderStatus.SUBMITTED.name());
        details.put("newStatus", savedOrder.getOrderStatus().name());

        auditService.recordEvent(
            AuditEventType.ORDER_REJECTED,
            order.getPortfolio().getCustomer().getUser(),
            savedOrder,
            attempt,
            null,
            null,
            details
        );
        return new TradeRejectedException(reason);
    }

    private QuoteSnapshot captureQuote(Instrument instrument) {
        Price price = pricingService
            .getLatestPriceBySymbol(instrument.getSymbol())
            .orElse(null);

        if (price == null) {
            return QuoteSnapshot.invalid();
        }

        Currency currency = price.getPriceCurrency();
        BigDecimal exchangeRate = currency == null
            ? null
            : currency.getCurrentExchangeRateToUsd();
        BigDecimal usdPrice = null;
        if (price.getPrice() != null
                && exchangeRate != null
                && exchangeRate.compareTo(BigDecimal.ZERO) > 0) {
            usdPrice = price.getPrice()
                .multiply(exchangeRate)
                .setScale(8, RoundingMode.HALF_UP);
        }

        return new QuoteSnapshot(
            price.getPrice(),
            currency,
            exchangeRate,
            usdPrice,
            price.getPriceTimestamp(),
            price.getSourceType(),
            price.getProviderName()
        );
    }

    private void recordPriceAudit(
            AuditEventType eventType,
            TradeOrder order,
            ExecutionAttempt attempt,
            QuoteSnapshot quote,
            String reason
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("executionAttemptId", attempt.getExecutionAttemptId());
        details.put("attemptStatus", attempt.getAttemptStatus().name());
        putIfPresent(details, "quotedPrice", quote.nativePrice());
        if (quote.currency() != null) {
            details.put("priceCurrencyCode", quote.currency().getCurrencyCode());
        }
        putIfPresent(details, "exchangeRateToUsd", quote.exchangeRateToUsd());
        putIfPresent(details, "quotedPriceUsd", quote.usdPrice());
        putIfPresent(details, "quoteTimestamp", quote.quoteTimestamp());
        if (quote.sourceType() != null) {
            details.put("sourceType", quote.sourceType().name());
        }
        putIfPresent(details, "providerName", quote.providerName());
        putIfPresent(details, "reason", reason);
        details.put("attemptedAt", attempt.getAttemptedAt());

        auditService.recordEvent(
            eventType,
            order.getPortfolio().getCustomer().getUser(),
            order,
            attempt,
            null,
            null,
            details
        );
    }

    private void recordHoldingAudit(
            TradeOrder order,
            Trade trade,
            Long holdingId,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            BigDecimal averageCostBefore,
            BigDecimal averageCostAfter
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        putIfPresent(details, "holdingId", holdingId);
        details.put("instrumentId", order.getInstrument().getInstrumentId());
        details.put("symbol", order.getInstrument().getSymbol());
        details.put("quantityBefore", quantityBefore);
        details.put("quantityAfter", quantityAfter);
        putIfPresent(details, "averageCostUsdBefore", averageCostBefore);
        putIfPresent(details, "averageCostUsdAfter", averageCostAfter);
        details.put("orderId", order.getOrderId());
        details.put("executionAttemptId", trade.getExecutionAttempt().getExecutionAttemptId());
        details.put("tradeId", trade.getTradeId());

        auditService.recordEvent(
            AuditEventType.HOLDING_CHANGED,
            order.getPortfolio().getCustomer().getUser(),
            order,
            trade.getExecutionAttempt(),
            trade,
            null,
            details
        );
    }

    private static void putIfPresent(
            Map<String, Object> details,
            String key,
            Object value
    ) {
        if (value != null) {
            details.put(key, value);
        }
    }

    private record QuoteSnapshot(
            BigDecimal nativePrice,
            Currency currency,
            BigDecimal exchangeRateToUsd,
            BigDecimal usdPrice,
            OffsetDateTime quoteTimestamp,
            PriceSourceType sourceType,
            String providerName
    ) {
        private static QuoteSnapshot invalid() {
            return new QuoteSnapshot(null, null, null, null, null, null, null);
        }

        private boolean isValid() {
            return nativePrice != null
                && nativePrice.compareTo(BigDecimal.ZERO) > 0
                && currency != null
                && exchangeRateToUsd != null
                && exchangeRateToUsd.compareTo(BigDecimal.ZERO) > 0
                && usdPrice != null
                && usdPrice.compareTo(BigDecimal.ZERO) > 0
                && quoteTimestamp != null
                && sourceType != null
                && providerName != null
                && !providerName.isBlank();
        }
    }

    /**
     * Map Trade entity to TradeConfirmationResponse DTO
     */
    private TradeConfirmationResponse mapToTradeConfirmation(Trade trade, TradeOrder order) {
        TradeConfirmationResponse response = new TradeConfirmationResponse();
        response.setTradeId(trade.getTradeId());
        response.setOrderId(trade.getOrder().getOrderId());
        response.setInstrumentSymbol(order.getInstrument().getSymbol());
        response.setOrderAction(order.getOrderAction().name());
        response.setExecutedQuantity(trade.getQuantity());
        response.setExecutionPrice(trade.getExecutionPrice());
        response.setTotalUsdValue(trade.getTotalUsdValue());
        response.setExecutedAt(trade.getExecutedAt());
        return response;
    }
}
