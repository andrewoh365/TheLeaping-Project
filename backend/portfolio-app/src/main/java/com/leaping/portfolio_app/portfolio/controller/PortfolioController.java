package com.leaping.portfolio_app.portfolio.controller;

import com.leaping.portfolio_app.auth.service.AuthService;
import com.leaping.portfolio_app.portfolio.dto.CashTransferRequest;
import com.leaping.portfolio_app.portfolio.dto.PortfolioResponse;
import com.leaping.portfolio_app.portfolio.dto.PortfolioSummaryResponse;
import com.leaping.portfolio_app.portfolio.service.CashService;
import com.leaping.portfolio_app.portfolio.service.PortfolioService;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio") //placeholder endpoint
public class PortfolioController {

    private final AuthService authService; 
    private final PortfolioService portfolioService; 
    private final CashService cashService;

    public PortfolioController(AuthService authService, PortfolioService portfolioService, CashService cashService){
        this.authService = authService; 
        this.portfolioService = portfolioService; 
        this.cashService = cashService;
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

    /**
     * Deposit to or withdraw from the signed-in client's cash balance.
     * No bank is connected in this phase; the movement is recorded in cash_transactions.
     *
     * @param request direction ("DEPOSIT" or "WITHDRAW") and amount in USD
     * @return the recorded movement and the new balance, or 400 with a readable message
     */
    @PostMapping("/cash")
    public ResponseEntity<?> transferCash(@RequestBody CashTransferRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long customerId = authService.getUserIdByEmail(auth.getName());
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(cashService.transfer(customerId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

}
