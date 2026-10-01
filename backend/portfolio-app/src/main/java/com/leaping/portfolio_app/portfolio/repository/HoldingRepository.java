package com.leaping.portfolio_app.portfolio.repository;

import com.leaping.portfolio_app.portfolio.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByPortfolio_PortfolioId(Long portfolioId);

}
