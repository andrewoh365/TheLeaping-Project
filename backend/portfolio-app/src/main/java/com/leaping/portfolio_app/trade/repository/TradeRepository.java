package com.leaping.portfolio_app.trade.repository;

import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.trade.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    Optional<Trade> findByOrderOrderId(Long orderId);
    Optional<Trade> findTopByOrderPortfolioOrderByExecutedAtDesc(Portfolio portfolio);
}
