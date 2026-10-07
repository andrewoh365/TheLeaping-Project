package com.leaping.portfolio_app.holdings;

import com.leaping.portfolio_app.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/holdings")
public class HoldingController {

    private final HoldingService holdingService;
    private final HoldingValidator holdingValidator;
    private final AuthService authService;

    public HoldingController(HoldingService holdingService, HoldingValidator holdingValidator, AuthService authService) {
        this.holdingService = holdingService;
        this.holdingValidator = holdingValidator;
        this.authService = authService;
    }

    /**
     * Get all holdings for a portfolio, sorted by most recently updated first.
     * Includes real-time market prices and P&L calculations.
     *
     * @param portfolioId the portfolio ID
     * @return ResponseEntity with list of holdings and their market data
     * @throws ResponseStatusException 404 if portfolio not found
     *
     * <h3>Response Codes</h3>
     * <ul>
     *   <li><strong>200 OK</strong> - Holdings retrieved successfully (may be empty list)</li>
     *   <li><strong>404 Not Found</strong> - Portfolio ID does not exist</li>
     *   <li><strong>401 Unauthorized</strong> - No valid JWT token provided</li>
     * </ul>
     */
    @GetMapping("/portfolio/{portfolioId}")
    public ResponseEntity<List<HoldingResponse>> getHoldingsByPortfolio(
            @PathVariable Long portfolioId
    ) {
        try {
            // Get authenticated customer ID
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            Long customerId = authService.getUserIdByEmail(email);
            
            // Validate portfolio exists AND belongs to customer
            holdingValidator.validatePortfolioOwnership(portfolioId, customerId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        
        List<HoldingResponse> holdings = holdingService.getHoldingsByPortfolio(portfolioId);
        return ResponseEntity.ok(holdings);
    }

    /**
     * Get a specific holding for a portfolio and instrument.
     * Includes real-time market price and P&L calculations.
     *
     * @param portfolioId the portfolio ID
     * @param instrumentId the instrument ID
     * @return ResponseEntity with holding details including market data
     * @throws ResponseStatusException 404 if portfolio or instrument not found, or if holding does not exist
     *
     * <h3>Response Codes</h3>
     * <ul>
     *   <li><strong>200 OK</strong> - Holding retrieved successfully</li>
     *   <li><strong>404 Not Found</strong> - Portfolio, instrument, or holding not found</li>
     *   <li><strong>401 Unauthorized</strong> - No valid JWT token provided</li>
     * </ul>
     */
    @GetMapping("/portfolio/{portfolioId}/instrument/{instrumentId}")
    public ResponseEntity<HoldingResponse> getHolding(
            @PathVariable Long portfolioId,
            @PathVariable Long instrumentId
    ) {
        try {
            // Get authenticated customer ID
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            Long customerId = authService.getUserIdByEmail(email);
            
            // Validate portfolio exists AND belongs to customer
            holdingValidator.validatePortfolioOwnership(portfolioId, customerId);
            holdingValidator.validateInstrument(instrumentId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        
        try {
            HoldingResponse holding = holdingService.getHolding(portfolioId, instrumentId);
            return ResponseEntity.ok(holding);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Holding not found for portfolio " + portfolioId + " and instrument " + instrumentId);
        }
    }

    /**
     * Search holdings by instrument symbol (case-insensitive).
     * Returns all holdings matching the symbol search term.
     * Includes real-time market prices and P&L calculations.
     *
     * @param portfolioId the portfolio ID
     * @param symbol the symbol to search for (e.g., "AAPL", "BTC", "EUR/USD")
     * @return ResponseEntity with list of matching holdings (may be empty)
     * @throws ResponseStatusException 400 if symbol parameter missing/empty, 404 if portfolio not found
     *
     * <h3>Response Codes</h3>
     * <ul>
     *   <li><strong>200 OK</strong> - Search completed successfully (may return empty list)</li>
     *   <li><strong>400 Bad Request</strong> - Symbol parameter missing or empty</li>
     *   <li><strong>404 Not Found</strong> - Portfolio ID does not exist</li>
     *   <li><strong>401 Unauthorized</strong> - No valid JWT token provided</li>
     * </ul>
     */
    @GetMapping("/portfolio/{portfolioId}/search")
    public ResponseEntity<List<HoldingResponse>> searchHoldingsBySymbol(
            @PathVariable Long portfolioId,
            @RequestParam(required = false) String symbol
    ) {
        // Validate symbol parameter
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Symbol parameter is required and cannot be empty"
            );
        }
        
        // Validate portfolio exists AND belongs to customer
        try {
            // Get authenticated customer ID
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            Long customerId = authService.getUserIdByEmail(email);
            
            holdingValidator.validatePortfolioOwnership(portfolioId, customerId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        
        List<HoldingResponse> results = holdingService.searchHoldingsBySymbol(portfolioId, symbol.trim());
        return ResponseEntity.ok(results);
    }
}
