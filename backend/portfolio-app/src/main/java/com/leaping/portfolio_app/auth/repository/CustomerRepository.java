package com.leaping.portfolio_app.auth.repository;

import com.leaping.portfolio_app.auth.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Customer entity
 * 
 * Provides database access methods for Customer records.
 * Used during registration to:
 * - Check if taxId already exists (prevent duplicates)
 * - Save new customer records
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByTaxId(String taxId);
}
