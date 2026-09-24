package com.leaping.portfolio_app.auth.dto;

public class AuthResponse {
    private String token;
    private String email;
    private String message;
    private boolean success;
    private String role;  // User's role (ADMIN, CUSTOMER, ANALYST)
    private String refreshToken;  // Refresh token for token renewal

    public AuthResponse() {
    }

    public AuthResponse(String token, String email, String message, boolean success) {
        this.token = token;
        this.email = email;
        this.message = message;
        this.success = success;
        this.role = null;
        this.refreshToken = null;
    }

    public AuthResponse(String token, String email, String message, boolean success, String role) {
        this.token = token;
        this.email = email;
        this.message = message;
        this.success = success;
        this.role = role;
        this.refreshToken = null;
    }

    public AuthResponse(String token, String email, String message, boolean success, String role, String refreshToken) {
        this.token = token;
        this.email = email;
        this.message = message;
        this.success = success;
        this.role = role;
        this.refreshToken = refreshToken;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
