package com.leaping.portfolio_app.auth.repository;

import com.leaping.portfolio_app.auth.model.Analyst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Analyst entity
 * 
 * Provides database access methods for Analyst records.
 * Used during analyst user creation/management.
 */
@Repository
public interface AnalystRepository extends JpaRepository<Analyst, Long> {
}
