package com.leaping.portfolio_app.auth.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Customer entity - child table of User (1:1 relationship)
 * 
 * When a user registers, we create:
 * 1. User record in users table (email, password, firstName, lastName, etc.)
 * 2. Customer record in customers table (dateOfBirth, taxId, linked via user_id)
 * 
 * Not every user is a customer - some are admins.
 * Admins don't have a customer record.
 */
@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "tax_id", nullable = false, unique = true)
    private String taxId;

    @OneToOne
    @MapsId  // Maps userId PK to User.id FK (creates 1:1 relationship)
    @JoinColumn(name = "user_id")
    private User user;

    public Customer() {
    }

    public Customer(Long userId, LocalDate dateOfBirth, String taxId) {
        this.userId = userId;
        this.dateOfBirth = dateOfBirth;
        this.taxId = taxId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
