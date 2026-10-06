package com.leaping.portfolio_app.portfolio.service;

import com.leaping.portfolio_app.holdings.HoldingResponse;
import com.leaping.portfolio_app.holdings.Holding;
import com.leaping.portfolio_app.holdings.HoldingRepository;
import com.leaping.portfolio_app.market.service.MarketPriceService;
import com.leaping.portfolio_app.portfolio.dto.PortfolioResponse;
import com.leaping.portfolio_app.portfolio.dto.PortfolioSummaryResponse;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository; 
    private final HoldingRepository holdingRepository;
    private final MarketPriceService marketPriceService;

    public PortfolioService(
            PortfolioRepository portfolioRepository, 
            HoldingRepository holdingRepository,
            MarketPriceService marketPriceService) {
        this.portfolioRepository = portfolioRepository; 
        this.holdingRepository = holdingRepository;
        this.marketPriceService = marketPriceService;
    }

    public PortfolioResponse getPortfolioForCustomer(Long customerId){
        //Find customer's portfolio if somehow doesn't exist, fail
        Portfolio portfolio = portfolioRepository.findByCustomer_UserId(customerId)
            .orElseThrow( () -> new RuntimeException("Portfolio not found for customer customerId:" + customerId) );
        
        //Portfolio found, find all holdings of that portfolio
        List<Holding> holdings = holdingRepository.findAllByPortfolio_PortfolioIdOrderByUpdatedAtDesc(portfolio.getPortfolioId() );

        //Take the holdings entities found and convert them into HoldingResponse DTO
        List<HoldingResponse> holdingResponses = new ArrayList<>(); 
        for (Holding holding : holdings){
            HoldingResponse response = new HoldingResponse(
                holding.getHoldingId(),
                holding.getInstrument().getInstrumentId(),
                holding.getInstrument().getSymbol(), 
                holding.getInstrument().getName(),
                holding.getQuantity(),
                holding.getAverageCostUsd()
            );
            holdingResponses.add(response);
        }

        // Packing everything into the outer PortfolioResponse
        return new PortfolioResponse(
            portfolio.getPortfolioId(),
            portfolio.getCashBalanceUsd(),
            holdingResponses
        );

    }

    /**
     * Get comprehensive portfolio summary with real-time market data and P&L calculations.
     * 
     * Fetches all holdings and enriches them with:
     * - Current market price for each holding
     * - Current value in USD (quantity × current price)
     * - Gain/Loss in USD and percentage
     * 
     * Calculates portfolio-level totals:
     * - Total cost basis (sum of all quantity × average cost)
     * - Total current value (cash + sum of all holdings current value)
     * - Total gain/loss (in USD and percentage)
     * 
     * @param customerId the customer ID
     * @return PortfolioSummaryResponse with full portfolio overview
     */
    public PortfolioSummaryResponse getPortfolioSummary(Long customerId) {
        // Fetch portfolio
        Portfolio portfolio = portfolioRepository.findByCustomer_UserId(customerId)
            .orElseThrow(() -> new RuntimeException("Portfolio not found for customer customerId:" + customerId));
        
        // Fetch all holdings
        List<Holding> holdings = holdingRepository.findAllByPortfolio_PortfolioIdOrderByUpdatedAtDesc(portfolio.getPortfolioId());
        
        // Initialize totals
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal totalHoldingsCurrentValue = BigDecimal.ZERO;
        List<HoldingResponse> enrichedHoldings = new ArrayList<>();
        
        // Enrich each holding with market data and P&L calculations
        for (Holding holding : holdings) {
            String symbol = holding.getInstrument().getSymbol();
            
            // Fetch current market price
            BigDecimal currentPrice = marketPriceService.getCurrentPrice(symbol);
            
            // Calculate holding values
            BigDecimal costBasis = holding.getAverageCostUsd().multiply(holding.getQuantity());
            BigDecimal currentValue = currentPrice.multiply(holding.getQuantity());
            BigDecimal gainLossUsd = currentValue.subtract(costBasis);
            BigDecimal gainLossPercent = BigDecimal.ZERO;
            
            // Calculate gain/loss percentage safely
            if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                gainLossPercent = gainLossUsd.divide(costBasis, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            }
            
            // Create enriched HoldingResponse with market data
            HoldingResponse response = new HoldingResponse(
                holding.getHoldingId(),
                holding.getInstrument().getInstrumentId(),
                symbol,
                holding.getInstrument().getName(),
                holding.getQuantity(),
                holding.getAverageCostUsd(),
                currentPrice,
                currentValue,
                gainLossUsd,
                gainLossPercent
            );
            
            enrichedHoldings.add(response);
            
            // Accumulate totals
            totalCostBasis = totalCostBasis.add(costBasis);
            totalHoldingsCurrentValue = totalHoldingsCurrentValue.add(currentValue);
        }
        
        // Calculate portfolio totals
        BigDecimal totalCurrentValue = portfolio.getCashBalanceUsd().add(totalHoldingsCurrentValue);
        BigDecimal totalGainLossUsd = totalCurrentValue.subtract(portfolio.getCashBalanceUsd()).subtract(totalCostBasis);
        BigDecimal totalGainLossPercent = BigDecimal.ZERO;
        
        if (totalCostBasis.compareTo(BigDecimal.ZERO) > 0) {
            totalGainLossPercent = totalGainLossUsd.divide(totalCostBasis, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }
        
        // For MVP, buying power = cash balance (simplified - no margin/leverage)
        BigDecimal buyingPower = portfolio.getCashBalanceUsd();
        
        return new PortfolioSummaryResponse(
            portfolio.getPortfolioId(),
            portfolio.getCustomer().getUserId(),
            portfolio.getCashBalanceUsd(),
            buyingPower,
            totalCostBasis,
            totalCurrentValue,
            totalGainLossUsd,
            totalGainLossPercent,
            enrichedHoldings
        );
    }

}
