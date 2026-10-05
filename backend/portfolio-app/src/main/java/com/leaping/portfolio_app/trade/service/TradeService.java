package com.leaping.portfolio_app.trade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.enums.PriceSourceType;
import com.leaping.portfolio_app.market.service.MarketPriceService;
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
    private final MarketPriceService priceService;

    public TradeService(
            TradeRepository tradeRepository,
            TradeOrderRepository tradeOrderRepository,
            ExecutionAttemptRepository executionAttemptRepository,
            PortfolioRepository portfolioRepository,
            HoldingRepository holdingRepository,
            CashTransactionRepository cashTransactionRepository,
            MarketPriceService priceService
    ) {
        this.tradeRepository = tradeRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.executionAttemptRepository = executionAttemptRepository;
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.priceService = priceService;
    }

    /**
     * Execute a submitted order.
     * 
     * Workflow:
     * 1. Validate order is in SUBMITTED status
     * 2. Get current market price
     * 3. Create ExecutionAttempt record
     * 4. Create Trade record
     * 5. Update holdings
     * 6. Update cash balance
     * 7. Mark order as FILLED
     * 
     * @param orderId The order to execute
     * @return Trade confirmation details
     * @throws IllegalArgumentException if order cannot be executed
     */
    public TradeConfirmationResponse executeTrade(Long orderId) {
        // 1. Load order
        TradeOrder order = tradeOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // 2. Validate order status
        if (!OrderStatus.SUBMITTED.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException(
                String.format("Order cannot be executed in %s status", order.getOrderStatus())
            );
        }

        Instrument instrument = order.getInstrument();

        // 3. Get current price
        BigDecimal currentPrice = priceService.getCurrentPrice(instrument.getSymbol());
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            rejectOrder(order, "No valid price available for execution");
            throw new IllegalArgumentException("Cannot execute: no current price available for " + instrument.getSymbol());
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

        return mapToTradeConfirmation(trade, order);
    }

    /**
     * Create ExecutionAttempt record for audit trail.
     */
    private ExecutionAttempt createExecutionAttempt(TradeOrder order, BigDecimal currentPrice) {
        ExecutionAttempt attempt = new ExecutionAttempt();
        attempt.setOrder(order);
        attempt.setAttemptNumber(1);
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

        Optional<Holding> existingHolding = holdingRepository.findByPortfolioAndInstrument(portfolio, instrument);

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
    private void updateCashBalance(TradeOrder order, Trade trade) {
        Portfolio portfolio = order.getPortfolio();
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
     * Reject an order with a specific reason.
     */
    private void rejectOrder(TradeOrder order, String reason) {
        order.setOrderStatus(OrderStatus.REJECTED);
        order.setRejectedAt(OffsetDateTime.now());
        order.setRejectionReason(reason);
        tradeOrderRepository.save(order);
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
