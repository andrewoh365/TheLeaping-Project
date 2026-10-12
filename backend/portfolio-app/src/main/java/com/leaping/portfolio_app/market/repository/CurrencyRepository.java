package com.leaping.portfolio_app.market.repository;

import com.leaping.portfolio_app.market.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, String> {
}
