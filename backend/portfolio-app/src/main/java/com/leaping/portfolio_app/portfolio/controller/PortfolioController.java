package com.leaping.portfolio_app.portfolio.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import com.leaping.portfolio_app.portfolio.service.PortfolioOverviewService;
import com.leaping.portfolio_app.trade.dto.PortfolioOverviewResponse;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {
    private final PortfolioOverviewService portfolioOverviewService;
    private final UserRepository userRepository;

    public PortfolioController(
        PortfolioOverviewService portfolioOverviewService,
        UserRepository userRepository
    ) {
        this.portfolioOverviewService = portfolioOverviewService;
        this.userRepository = userRepository;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(Authentication authentication) {
        try {
            Long userId = resolveCurrentUserId(authentication);
            PortfolioOverviewResponse overview = portfolioOverviewService.getPortfolioOverview(userId);
            return ResponseEntity.ok(overview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to load portfolio overview: " + e.getMessage()));
        }
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
        return user.getId();
    }

    public static class ErrorResponse {
        private final String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}