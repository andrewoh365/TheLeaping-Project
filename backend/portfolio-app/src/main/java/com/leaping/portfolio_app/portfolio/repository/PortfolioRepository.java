package com.leaping.portfolio_app.portfolio.repository;

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Optional<Portfolio> findByCustomer(Customer customer);

    Optional<Portfolio> findByCustomer_UserId(Long userId);
}
