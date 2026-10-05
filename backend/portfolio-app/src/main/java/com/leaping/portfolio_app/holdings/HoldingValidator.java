package com.leaping.portfolio_app.holdings;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

@Component
public class HoldingValidator {
    private final InstrumentRepository instrumentRepository;
    private final PortfolioRepository portfolioRepository;

    public HoldingValidator(InstrumentRepository instrumentRepository, PortfolioRepository portfolioRepository) {
        this.instrumentRepository = instrumentRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than 0");
        }
    }

    public void validateAverageCost(BigDecimal averageCost) {
        if (averageCost == null || averageCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Average cost cannot be negative");
        }
    }

    public void validateInstrument(Long instrumentId) {
        if (!instrumentRepository.existsById(instrumentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found");
        }
        Instrument instrument = instrumentRepository.findById(instrumentId).orElseThrow();
        if (!instrument.getIsTradeable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Instrument is not tradeable");
        }
    }

    public void validatePortfolioExists(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found");
        }
    }
}