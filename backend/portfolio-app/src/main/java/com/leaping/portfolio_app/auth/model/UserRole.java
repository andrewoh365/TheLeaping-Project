package com.leaping.portfolio_app.auth.model;

/**
 * Enum for user roles
 * Defines the different types of users in the system
 */
public enum UserRole {
    ADMIN,    // Full system access - can manage users, view all data
    CLIENT    // Limited access - can only access own data
}
