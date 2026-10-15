package com.leaping.portfolio_app.auth.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.leaping.portfolio_app.auth.model.Customer;

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

    @Query(value = """
            SELECT c.*
            FROM customers c
            JOIN users u ON u.user_id = c.user_id
            WHERE (
                :search = ''
                OR CAST(c.user_id AS VARCHAR) LIKE CONCAT('%', :search, '%')
                OR LOWER(COALESCE(u.first_name, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(COALESCE(u.last_name, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(COALESCE(u.email, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(CONCAT(
                    COALESCE(u.first_name, ''), ' ',
                    COALESCE(u.last_name, '')
                )) LIKE CONCAT('%', LOWER(:search), '%')
            )
            AND (
                :status = ''
                OR UPPER(COALESCE(u.status, '')) = UPPER(:status)
            )
            ORDER BY c.user_id ASC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM customers c
            JOIN users u ON u.user_id = c.user_id
            WHERE (
                :search = ''
                OR CAST(c.user_id AS VARCHAR) LIKE CONCAT('%', :search, '%')
                OR LOWER(COALESCE(u.first_name, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(COALESCE(u.last_name, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(COALESCE(u.email, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(CONCAT(
                    COALESCE(u.first_name, ''), ' ',
                    COALESCE(u.last_name, '')
                )) LIKE CONCAT('%', LOWER(:search), '%')
            )
            AND (
                :status = ''
                OR UPPER(COALESCE(u.status, '')) = UPPER(:status)
            )
            """,
            nativeQuery = true)
    Page<Customer> findPage(
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable
    );
}
