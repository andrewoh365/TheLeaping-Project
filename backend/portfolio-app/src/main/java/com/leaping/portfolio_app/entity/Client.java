package com.leaping.portfolio_app.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Getter
@Setter
@Entity 
@Table(name = "client")
public class Client {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName; 

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;


    @Column(name = "tax_id", nullable = false, unique = true, length = 100)
    private String taxId; 

    @Column(name = "email", unique = true, length = 255)
    private String email;
    
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "password_hash")
    private String passwordHash; 


    //Might be better practice to put enums in seperate file.
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.CUSTOMER;
    
    public enum Role{
        ADMIN,
        CUSTOMER
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.INACTIVE;
    
    public enum Status{
        ACTIVE,
        INACTIVE,
        LOCKED
    }




    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "last_login")
    private OffsetDateTime lastLogin;

    public void setPassword(String rawPassword) {
        // TODO: Implement password hashing logic (e.g., BCrypt)
        this.passwordHash = rawPassword;
    }
}
