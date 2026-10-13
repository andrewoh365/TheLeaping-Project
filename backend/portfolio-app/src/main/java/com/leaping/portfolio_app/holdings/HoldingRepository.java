package com.leaping.portfolio_app.holdings;

import com.leaping.portfolio_app.holdings.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Search holdings by instrument symbol or name (case insensitive)
    @Query("""
        select h from Holding h
        where h.portfolio.portfolioId = :portfolioId
          and (lower(h.instrument.symbol) like lower(concat('%', :query, '%'))
            or lower(h.instrument.name) like lower(concat('%', :query, '%')))
        order by h.updatedAt desc
        """)
    List<Holding> searchByPortfolioAndInstrument(@Param("portfolioId") Long portfolioId, @Param("query") String query);
}
