package com.leaping.portfolio_app.auth.dto;

/**
 * DTO (Data Transfer Object) for user registration
 * This holds the data that comes from the frontend registration form
 * 
 * When user submits registration, the form sends JSON like:
 * {
 *   "email": "john@example.com",
 *   "password": "SecurePass123!",
 *   "confirmPassword": "SecurePass123!",
 *   "firstName": "John",
 *   "lastName": "Doe"
 * }
 */
public class RegisterRequest {
    private String email;
    private String password;
    private String confirmPassword;  // User types password twice to catch typos
    private String firstName;
    private String lastName;

    // Empty constructor (Spring needs this for JSON parsing)
    public RegisterRequest() {
    }

    // Constructor with all fields
    public RegisterRequest(String email, String password, String confirmPassword, 
                          String firstName, String lastName) {
        this.email = email;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    // ========== GETTERS AND SETTERS ==========
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
