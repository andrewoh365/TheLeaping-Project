package com.leaping.portfolio_app.portfolio.service;

import com.leaping.portfolio_app.portfolio.dto.HoldingResponse;
import com.leaping.portfolio_app.portfolio.dto.PortfolioResponse;
import com.leaping.portfolio_app.portfolio.entity.Holding;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.HoldingRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository; 
    private final HoldingRepository holdingRepository; 

    public PortfolioService(PortfolioRepository portfolioRepository, HoldingRepository holdingRepository){
        this.portfolioRepository = portfolioRepository; 
        this.holdingRepository = holdingRepository; 
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
            HoldingResponse response = new HoldingResponse(
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

}
