package com.leaping.portfolio_app.trade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.dto.TradabilityDto;
import com.leaping.portfolio_app.market.enums.PriceSourceType;
import com.leaping.portfolio_app.market.enums.TradabilityReason;
import com.leaping.portfolio_app.market.service.BidAskQuote;
import com.leaping.portfolio_app.market.service.MarketPriceService;
import com.leaping.portfolio_app.market.service.PreTradeMarketService;
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
import com.leaping.portfolio_app.trade.exception.OrderNotExecutableException;
import com.leaping.portfolio_app.trade.exception.TradeRejectedException;
import com.leaping.portfolio_app.trade.repository.ExecutionAttemptRepository;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

/**
 * TradeService handles trade execution and settlement of accepted orders.
 * It is called by OrderExecutionWorker after the acceptance has committed.
 * 
 * Responsibilities:
 * - Execute accepted orders with current market prices
 * - Create Trade and ExecutionAttempt records
 * - Update customer holdings
 * - Update cash balance
 * - Record transaction audit trail
 * - Handle error cases (insufficient shares, price unavailable, etc.)
 */
@Service
@Transactional
public class TradeService {
    private static final Logger log = LoggerFactory.getLogger(TradeService.class);

    private final TradeRepository tradeRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final ExecutionAttemptRepository executionAttemptRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final MarketPriceService priceService;
    private final PreTradeMarketService preTradeMarketService;
    private final EntityManager entityManager;

    /** Same switch as TradeOrderService: re-check market hours and price freshness at fill time. */
    @Value("${trading.enforce-tradability:true}")
    private boolean enforceTradability = true;

    public TradeService(
            TradeRepository tradeRepository,
            TradeOrderRepository tradeOrderRepository,
            ExecutionAttemptRepository executionAttemptRepository,
            PortfolioRepository portfolioRepository,
            HoldingRepository holdingRepository,
            CashTransactionRepository cashTransactionRepository,
            MarketPriceService priceService,
            PreTradeMarketService preTradeMarketService,
            EntityManager entityManager
    ) {
        this.tradeRepository = tradeRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.executionAttemptRepository = executionAttemptRepository;
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.priceService = priceService;
        this.preTradeMarketService = preTradeMarketService;
        this.entityManager = entityManager;
    }

    /**
     * Execute an accepted order.
     *
     * Workflow:
     * 1. Lock the order and check it is ACCEPTED (still awaiting execution)
     * 2. Lock the portfolio so concurrent orders on it are serialised
     * 3. Check the market is still open, then price the fill at the ask (BUY) or bid (SELL)
     * 4. Re-check cash (BUY) or holding (SELL) against the execution price
     * 5. Create ExecutionAttempt and Trade records
     * 6. Update holdings and cash balance
     * 7. Mark order as FILLED
     *
     * All rejection checks run before anything is written, so a rejection never
     * leaves a partial fill behind. Rejections throw TradeRejectedException, which
     * does not roll back, so the REJECTED status and reason are persisted.
     * Any other failure rolls back entirely and leaves the order ACCEPTED, so it can be retried;
     * the caller records the failed attempt with recordFailedAttempt.
     *
     * @param orderId The order to execute
     * @return Trade confirmation details
     * @throws TradeRejectedException if the order was rejected (and recorded as such)
     * @throws OrderNotExecutableException if the order is no longer awaiting execution
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

        if (!OrderStatus.ACCEPTED.equals(order.getOrderStatus())) {
            throw new OrderNotExecutableException(
                String.format("Order cannot be executed in %s status", order.getOrderStatus())
            );
        }
        // Filling a limit order at market would ignore the client's price (limit execution is future work)
        if (!OrderType.MARKET.equals(order.getOrderType())) {
            throw new OrderNotExecutableException(
                String.format("%s orders are not executed at market", order.getOrderType())
            );
        }

        // 2. Lock portfolio (held until commit). Every cash/holding change for this portfolio
        // goes through this lock, so concurrent orders are serialised.
        Portfolio portfolio = order.getPortfolio();
        entityManager.refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

        Instrument instrument = order.getInstrument();

        // 3. The market may have closed, or the price gone stale, since the order was accepted
        if (enforceTradability) {
            TradabilityDto tradability = preTradeMarketService.getTradability(instrument.getSymbol());
            if (!tradability.isTradable()) {
                throw reject(order, tradability.getReason() == TradabilityReason.MARKET_CLOSED
                    ? "The market closed before your order could be filled."
                    : String.format("We don't have an up-to-date price for %s, so the order couldn't be filled.",
                        instrument.getSymbol()));
            }
        }

        // Price the fill: buys pay the ask, sells receive the bid
        BidAskQuote quote = BidAskQuote.fromMid(
            priceService.getCurrentPrice(instrument.getSymbol()), instrument.getInstrumentType());
        if (quote == null) {
            throw reject(order, String.format(
                "We don't have a current price for %s, so the order couldn't be filled.", instrument.getSymbol()));
        }
        BigDecimal executionPrice = quote.priceFor(OrderAction.BUY.equals(order.getOrderAction()));

        // 4. Re-check funds under the lock, at the actual execution price
        BigDecimal totalUsdValue = executionPrice.multiply(order.getQuantity());
        if (OrderAction.BUY.equals(order.getOrderAction())) {
            if (portfolio.getCashBalanceUsd().compareTo(totalUsdValue) < 0) {
                throw reject(order, String.format(
                    "Not enough cash. This order costs $%,.2f and you have $%,.2f available.",
                    totalUsdValue, portfolio.getCashBalanceUsd()));
            }
        } else {
            Optional<Holding> holding = holdingRepository
                .findByPortfolio_PortfolioIdAndInstrument_InstrumentId(
                    portfolio.getPortfolioId(), instrument.getInstrumentId());
            // Re-read under the portfolio lock in case a stale copy is cached.
            holding.ifPresent(entityManager::refresh);
            BigDecimal owned = holding.map(Holding::getQuantity).orElse(BigDecimal.ZERO);
            if (owned.compareTo(order.getQuantity()) < 0) {
                throw reject(order, owned.compareTo(BigDecimal.ZERO) <= 0
                    ? String.format("You don't own any %s to sell.", instrument.getSymbol())
                    : String.format("You only own %s %s.",
                        owned.stripTrailingZeros().toPlainString(), instrument.getSymbol()));
            }
        }

        // 5. Create ExecutionAttempt and Trade records
        ExecutionAttempt attempt = createExecutionAttempt(order, executionPrice);
        Trade trade = createTrade(order, attempt, executionPrice);

        // 6. Update holdings and cash
        updateHoldings(order, trade);
        updateCashBalance(portfolio, order, trade);

        // 7. Update order status to FILLED
        order.fill();
        tradeOrderRepository.save(order);

        return mapToTradeConfirmation(trade, order);
    }

    /**
     * Record an execution attempt that failed for a technical reason (executeTrade rolled back).
     * The order stays ACCEPTED so it can be retried; once maxAttempts have failed it is
     * rejected, so a client is never left with an order that silently never executes.
     *
     * @return true if the order was rejected because it ran out of attempts
     */
    public boolean recordFailedAttempt(Long orderId, String reason, int maxAttempts) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        if (!OrderStatus.ACCEPTED.equals(order.getOrderStatus())) {
            return false;
        }

        int attemptNumber = (int) executionAttemptRepository.countByOrderOrderId(orderId) + 1;
        ExecutionAttempt attempt = new ExecutionAttempt(order, attemptNumber, ExecutionAttemptStatus.FAILED);
        attempt.setReason(reason);
        executionAttemptRepository.save(attempt);

        if (attemptNumber < maxAttempts) {
            return false;
        }
        log.warn("Order {} rejected after {} failed execution attempts", orderId, attemptNumber);
        order.reject("We couldn't complete your order. No money has been taken and your holdings are unchanged.");
        tradeOrderRepository.save(order);
        return true;
    }

    /**
     * Create ExecutionAttempt record for audit trail.
     */
    private ExecutionAttempt createExecutionAttempt(TradeOrder order, BigDecimal currentPrice) {
        ExecutionAttempt attempt = new ExecutionAttempt();
        attempt.setOrder(order);
        attempt.setAttemptNumber((int) executionAttemptRepository.countByOrderOrderId(order.getOrderId()) + 1);
        attempt.setQuotedPrice(currentPrice);
        attempt.setPriceCurrency(order.getInstrument().getPriceCurrency());
        attempt.setExchangeRateToUsd(BigDecimal.ONE);  // Assuming prices are in USD
        attempt.setQuotedPriceUsd(currentPrice);
        attempt.setQuoteTimestamp(OffsetDateTime.now());
        attempt.setSourceType(PriceSourceType.MOCK);
        attempt.setProviderName("MarketPriceService");
        attempt.setAttemptStatus(ExecutionAttemptStatus.FILLED);
        attempt.setAttemptedAt(OffsetDateTime.now());

        return executionAttemptRepository.save(attempt);
    }

    /**
     * Create Trade record when order is filled.
     */
    private Trade createTrade(TradeOrder order, ExecutionAttempt attempt, BigDecimal executionPrice) {
        Trade trade = new Trade();
        trade.setOrder(order);
        trade.setExecutionAttempt(attempt);
        trade.setQuantity(order.getQuantity());
        trade.setExecutionPrice(executionPrice);
        trade.setExecutionPriceCurrency(order.getInstrument().getPriceCurrency());
        trade.setExchangeRateToUsdAtExecution(BigDecimal.ONE);
        
        // Calculate total USD value
        BigDecimal totalUsdValue = executionPrice.multiply(order.getQuantity());
        trade.setExecutionPriceUsd(executionPrice);
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
                    return;
                }

                holding.setQuantity(newQuantity);
                // Average cost stays the same
            }

            holding.setUpdatedAt(OffsetDateTime.now());
            holdingRepository.save(holding);

        } else {
            // New holding (only valid for BUY)
            if (!OrderAction.BUY.equals(order.getOrderAction())) {
                throw new IllegalArgumentException("Cannot sell - no existing holding for this instrument");
            }

            Holding newHolding = new Holding();
            newHolding.setPortfolio(portfolio);
            newHolding.setInstrument(instrument);
            newHolding.setQuantity(trade.getQuantity());
            newHolding.setAverageCostUsd(trade.getExecutionPrice());
            newHolding.setUpdatedAt(OffsetDateTime.now());

            holdingRepository.save(newHolding);
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
    private void updateCashBalance(Portfolio portfolio, TradeOrder order, Trade trade) {
        BigDecimal balanceBefore = portfolio.getCashBalanceUsd();
        BigDecimal transactionAmount;
        BigDecimal balanceAfter;

        if (OrderAction.BUY.equals(order.getOrderAction())) {
            // BUY: Negative impact on cash (spending money)
            transactionAmount = trade.getTotalUsdValue().negate();
            balanceAfter = balanceBefore.subtract(trade.getTotalUsdValue());
            
        } else {
            // SELL: Positive impact on cash (receiving money)
            transactionAmount = trade.getTotalUsdValue();
            balanceAfter = balanceBefore.add(trade.getTotalUsdValue());
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

        cashTransactionRepository.save(transaction);
    }

    /**
     * Mark an order REJECTED with a reason and return the exception to throw.
     * The caller must throw it so the transaction commits the rejection.
     */
    private TradeRejectedException reject(TradeOrder order, String reason) {
        order.reject(reason);
        tradeOrderRepository.save(order);
        return new TradeRejectedException(reason);
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
