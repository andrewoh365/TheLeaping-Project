package com.leaping.portfolio_app.auth.dto;

public class AuthResponse {
    private String token;
    private String email;
    private String message;
    private boolean success;

    public AuthResponse() {
    }

    public AuthResponse(String token, String email, String message, boolean success) {
        this.token = token;
        this.email = email;
        this.message = message;
        this.success = success;
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
}
