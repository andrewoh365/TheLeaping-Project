package com.leaping.portfolio_app.portfolio.repository;

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Optional<Portfolio> findByCustomer(Customer customer);

    Optional<Portfolio> findByCustomerUserId(Long customerId);
    Optional<Portfolio> findByCustomer_UserId(Long userId);
}
