package com.leaping.portfolio_app.portfolio.controller;

import com.leaping.portfolio_app.auth.service.AuthService;
import com.leaping.portfolio_app.portfolio.dto.PortfolioResponse;
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

}
