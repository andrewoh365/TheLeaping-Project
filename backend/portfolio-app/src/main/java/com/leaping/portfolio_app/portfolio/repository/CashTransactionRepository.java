package com.leaping.portfolio_app.portfolio.repository;

import com.leaping.portfolio_app.portfolio.entity.CashTransaction;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {
    List<CashTransaction> findByPortfolioOrderByCreatedAtDesc(Portfolio portfolio);
    List<CashTransaction> findByPortfolio(Portfolio portfolio);
}
