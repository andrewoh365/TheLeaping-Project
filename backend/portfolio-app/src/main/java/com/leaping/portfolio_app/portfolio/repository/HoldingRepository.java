package com.leaping.portfolio_app.portfolio.repository;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.portfolio.entity.Holding;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
	Optional<Holding> findByPortfolioAndInstrument(Portfolio portfolio, Instrument instrument);

	List<Holding> findByPortfolio(Portfolio portfolio);

	List<Holding> findByPortfolioAndQuantityGreaterThan(Portfolio portfolio, BigDecimal quantity);

	List<Holding> findByPortfolio_PortfolioId(Long portfolioId);
}
