package com.leaping.portfolio_app.market.repository;

import com.leaping.portfolio_app.market.entity.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketRepository extends JpaRepository<Market, Long> {

    List<Market> findByIsActiveTrueOrderByMarketNameAsc();

    Optional<Market> findByMarketName(String marketName);

    Optional<Market> findByMarketNameIgnoreCase(String marketName);
}
