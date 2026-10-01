package com.leaping.portfolio_app.holdings;

import com.leaping.portfolio_app.holdings.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {
    
    List<Holding> findAllByPortfolio_PortfolioIdOrderByUpdatedAtDesc(Long portfolioId);

    Optional<Holding> findByPortfolio_PortfolioIdAndInstrument_InstrumentId(Long portfolioId, Long instrumentId);

    boolean existsByPortfolio_PortfolioIdAndInstrument_InstrumentId(Long portfolioId, Long instrumentId);

    void deleteByPortfolio_PortfolioIdAndInstrument_InstrumentId(Long portfolioId, Long instrumentId);

    void deleteAllByPortfolio_PortfolioId(Long portfolioId);
}
