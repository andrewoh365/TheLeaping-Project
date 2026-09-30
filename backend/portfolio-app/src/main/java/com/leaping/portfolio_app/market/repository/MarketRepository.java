package com.leaping.portfolio_app.market.repository;

import com.leaping.portfolio_app.market.entity.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketRepository extends JpaRepository<Market, Long> {
    Optional<Market> findByMarketName(String marketName);
}
