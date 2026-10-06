package com.leaping.portfolio_app.portfolio.controller;

import com.leaping.portfolio_app.auth.service.AuthService;
import com.leaping.portfolio_app.portfolio.dto.PortfolioResponse;
import com.leaping.portfolio_app.portfolio.dto.PortfolioSummaryResponse;
import com.leaping.portfolio_app.portfolio.service.PortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio") //placeholder endpoint
public class PortfolioController {

    private final AuthService authService; 
    private final PortfolioService portfolioService; 

    public PortfolioController(AuthService authService, PortfolioService portfolioService){
        this.authService = authService; 
        this.portfolioService = portfolioService; 
    }

    @GetMapping
    public ResponseEntity<PortfolioResponse> getMyPortfolio() {

        //Get email from JwtAuthenticationFilter
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        //Get id from  AuthService method using email
        Long customerId = authService.getUserIdByEmail(email);

        // Get portfolio using id
        PortfolioResponse response = portfolioService.getPortfolioForCustomer(customerId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get comprehensive portfolio summary with real-time market data and P&L
     * 
     * Returns:
     * - Portfolio identifiers (ID, customer ID)
     * - Cash balance and buying power
     * - Total cost basis, current value, and overall gain/loss
     * - All holdings with current prices and individual P&L
     * 
     * @return PortfolioSummaryResponse with full portfolio overview
     */
    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryResponse> getPortfolioSummary() {
        
        // Get email from JwtAuthenticationFilter
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        
        // Get customer ID from AuthService
        Long customerId = authService.getUserIdByEmail(email);
        
        // Get portfolio summary with market data and P&L
        PortfolioSummaryResponse summary = portfolioService.getPortfolioSummary(customerId);
        
        return ResponseEntity.ok(summary);
    }

}
