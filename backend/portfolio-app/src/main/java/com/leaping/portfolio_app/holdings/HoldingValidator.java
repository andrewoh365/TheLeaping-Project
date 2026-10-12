package com.leaping.portfolio_app.holdings;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Validator for Holdings operations.
 * Throws RuntimeException with descriptive messages for validation failures.
 * Controller converts these to appropriate HTTP status codes.
 */
@Component
public class HoldingValidator {
    private final InstrumentRepository instrumentRepository;
    private final PortfolioRepository portfolioRepository;

    public HoldingValidator(InstrumentRepository instrumentRepository, PortfolioRepository portfolioRepository) {
        this.instrumentRepository = instrumentRepository;
        this.portfolioRepository = portfolioRepository;
    }

    /**
     * Validate that quantity is positive
     * @param quantity the quantity to validate
     * @throws RuntimeException if quantity is null or <= 0
     */
    public void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Quantity must be greater than 0");
        }
    }

    /**
     * Validate that average cost is non-negative
     * @param averageCost the average cost to validate
     * @throws RuntimeException if average cost is null or < 0
     */
    public void validateAverageCost(BigDecimal averageCost) {
        if (averageCost == null || averageCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Average cost cannot be negative");
        }
    }

    /**
     * Validate that instrument exists and is tradeable
     * @param instrumentId the instrument ID to validate
     * @throws RuntimeException if instrument not found or not tradeable
     */
    public void validateInstrument(Long instrumentId) {
        if (instrumentId == null || instrumentId <= 0) {
            throw new RuntimeException("Invalid instrument ID");
        }
        
        if (!instrumentRepository.existsById(instrumentId)) {
            throw new RuntimeException("Instrument not found with ID: " + instrumentId);
        }
        
        Instrument instrument = instrumentRepository.findById(instrumentId).orElseThrow();
        if (!instrument.getIsTradeable()) {
            throw new RuntimeException("Instrument '" + instrument.getSymbol() + "' is not tradeable");
        }
    }

    /**
     * Validate that portfolio exists
     * @param portfolioId the portfolio ID to validate
     * @throws RuntimeException if portfolio not found
     */
    public void validatePortfolioExists(Long portfolioId) {
        if (portfolioId == null || portfolioId <= 0) {
            throw new RuntimeException("Invalid portfolio ID");
        }
        
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new RuntimeException("Portfolio not found with ID: " + portfolioId);
        }
    }

    /**
     * Validate that portfolio exists AND belongs to the specified customer.
     * CRITICAL FOR SECURITY: Ensures customer can only access their own portfolios.
     * 
     * @param portfolioId the portfolio ID to validate
     * @param customerId the customer ID to verify ownership
     * @throws RuntimeException if portfolio not found or does not belong to customer
     */
    public void validatePortfolioOwnership(Long portfolioId, Long customerId) {
        if (portfolioId == null || portfolioId <= 0) {
            throw new RuntimeException("Invalid portfolio ID");
        }
        
        if (customerId == null || customerId <= 0) {
            throw new RuntimeException("Invalid customer ID");
        }
        
        // Check if portfolio exists AND belongs to this customer
        boolean portfolioExists = portfolioRepository.findByCustomer_UserId(customerId)
            .map(portfolio -> portfolio.getPortfolioId().equals(portfolioId))
            .orElse(false);
        
        if (!portfolioExists) {
            throw new RuntimeException("Portfolio not found or access denied");
        }
    }
}