package com.leaping.portfolio_app.holdings;

import com.leaping.portfolio_app.holdings.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {
    
    // Get all holdings for a portfolio, newest first
    List<Holding> findAllByPortfolio_PortfolioIdOrderByUpdatedAtDesc(Long portfolioId);

    // Get specific holding by portfolio and instrument IDs
    Optional<Holding> findByPortfolio_PortfolioIdAndInstrument_InstrumentId(Long portfolioId, Long instrumentId);

    // Check if holding exists
    boolean existsByPortfolio_PortfolioIdAndInstrument_InstrumentId(Long portfolioId, Long instrumentId);

    // Delete a specific holding by portfolio and instrument IDs
    void deleteByPortfolio_PortfolioIdAndInstrument_InstrumentId(Long portfolioId, Long instrumentId);

    // Delete all holdings for a specific portfolio
    void deleteAllByPortfolio_PortfolioId(Long portfolioId);

    // Search holdings by instrument symbol (case insensitive)
    List<Holding> findByPortfolio_PortfolioIdAndInstrument_SymbolContainingIgnoreCase(Long portfolioId, String symbol);
}
