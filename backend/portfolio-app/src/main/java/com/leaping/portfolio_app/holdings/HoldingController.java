package com.leaping.portfolio_app.holdings;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holdings")
public class HoldingController {

    private final HoldingService holdingService;
    private final HoldingValidator holdingValidator;

    public HoldingController(HoldingService holdingService, HoldingValidator holdingValidator) {
        this.holdingService = holdingService;
        this.holdingValidator = holdingValidator;
    }

    /**
     * Get all holdings for a portfolio
     * 
     * @param portfolioId the portfolio ID
     * @return list of holdings for the portfolio
     */
    @GetMapping("/portfolio/{portfolioId}")
    @ResponseStatus(HttpStatus.OK)
    public List<HoldingResponse> getHoldingsByPortfolio(
            @PathVariable Long portfolioId
    ) {
        holdingValidator.validatePortfolioExists(portfolioId);
        return holdingService.getHoldingsByPortfolio(portfolioId);
    }

    /**
     * Get a specific holding for a portfolio and instrument
     * 
     * @param portfolioId the portfolio ID
     * @param instrumentId the instrument ID
     * @return the holding details
     */
    @GetMapping("/portfolio/{portfolioId}/instrument/{instrumentId}")
    @ResponseStatus(HttpStatus.OK)
    public HoldingResponse getHolding(
            @PathVariable Long portfolioId,
            @PathVariable Long instrumentId
    ) {
        holdingValidator.validatePortfolioExists(portfolioId);
        holdingValidator.validateInstrument(instrumentId);
        return holdingService.getHolding(portfolioId, instrumentId);
    }

    /**
     * Search holdings by instrument symbol
     * 
     * @param portfolioId the portfolio ID
     * @param symbol the symbol to search for (case insensitive)
     * @return list of holdings matching the symbol
     */
    @GetMapping("/portfolio/{portfolioId}/search")
    @ResponseStatus(HttpStatus.OK)
    public List<HoldingResponse> searchHoldingsBySymbol(
            @PathVariable Long portfolioId,
            @RequestParam String symbol
    ) {
        holdingValidator.validatePortfolioExists(portfolioId);
        
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Symbol parameter is required"
            );
        }
        
        return holdingService.searchHoldingsBySymbol(portfolioId, symbol);
    }
}
