package com.leaping.portfolio_app.portfolio.service;

import com.leaping.portfolio_app.market.service.PricingService;
import com.leaping.portfolio_app.portfolio.dto.HoldingResponse;
import com.leaping.portfolio_app.portfolio.dto.PortfolioResponse;
import com.leaping.portfolio_app.portfolio.entity.Holding;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.HoldingRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import com.leaping.portfolio_app.market.entity.Price;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal; 
import java.math.RoundingMode; 
import java.util.Optional; //allows for possibly having exactly 1 or 0 value or nothing.

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository; 
    private final HoldingRepository holdingRepository; 
    private final PricingService pricingService; 

    public PortfolioService(PortfolioRepository portfolioRepository, HoldingRepository holdingRepository, 
                             PricingService pricingService){
        this.portfolioRepository = portfolioRepository; 
        this.holdingRepository = holdingRepository; 
        this.pricingService = pricingService;
    }

    public PortfolioResponse getPortfolioForCustomer(Long customerId){
        //Find customer's portfolio if somehow doesn't exist, fail
        Portfolio portfolio = portfolioRepository.findByCustomer_UserId(customerId)
            .orElseThrow( () -> new RuntimeException("Portfolio not found for customer customerId:" + customerId) );
        
        //Portfolio found, find all holdings of that portfolio
        List<Holding> holdings = holdingRepository.findByPortfolio_PortfolioId(portfolio.getPortfolioId() );

        //Take the holdings entities found and convert them into HoldingResponse DTO
        List<HoldingResponse> holdingResponses = new ArrayList<>(); 
        for (Holding holding : holdings){

            //Look up instruments latest price (does not exist yet)
            Optional<Price> latestPrice = pricingService.getLatestPriceBySymbol(
                    holding.getInstrument().getSymbol());
            
            //If no price found or not in USD, =null
            BigDecimal currentPriceUsd = null;
            BigDecimal marketValueUsd = null;
            BigDecimal gainLossUsd = null;
            BigDecimal gainLossPercent = null;

            boolean priceAvailableInUsd = latestPrice.isPresent()
                    && "USD".equals(latestPrice.get().getPriceCurrency().getCurrencyCode());
            
            if(priceAvailableInUsd){
                currentPriceUsd = latestPrice.get().getPrice();
                marketValueUsd = holding.getQuantity().multiply(currentPriceUsd);

                BigDecimal costBasis = holding.getQuantity().multiply(holding.getAverageCostUsd());
                gainLossUsd = marketValueUsd.subtract(costBasis);

                // edge case if cost basis = 0 (0 can't be multiplied)
                if (costBasis.compareTo(BigDecimal.ZERO) != 0) {
                    gainLossPercent = gainLossUsd
                            .divide(costBasis, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));

                }
            }

            HoldingResponse response = new HoldingResponse(
                holding.getInstrument().getSymbol(), 
                holding.getInstrument().getName(),
                holding.getQuantity(),
                holding.getAverageCostUsd(),
                currentPriceUsd,
                marketValueUsd,
                gainLossUsd,
                gainLossPercent
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

}
