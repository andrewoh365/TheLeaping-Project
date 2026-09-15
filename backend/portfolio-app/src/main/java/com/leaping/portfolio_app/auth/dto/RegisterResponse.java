package com.leaping.portfolio_app.auth.dto;

/**
 * DTO for registration response
 * This is what we send back to the frontend after registration attempt
 * 
 * Success example:
 * {
 *   "token": "eyJhbGciOiJIUzUxMiJ9...",
 *   "email": "john@example.com",
 *   "firstName": "John",
 *   "lastName": "Doe",
 *   "message": "User created successfully",
 *   "success": true
 * }
 * 
 * Failure example:
 * {
 *   "token": null,
 *   "email": null,
 *   "firstName": null,
 *   "lastName": null,
 *   "message": "Email already exists",
 *   "success": false
 * }
 */
public class RegisterResponse {
    private String token;      // JWT token for the newly created user
    private String email;      // The user's email
    private String firstName;  // User's first name
    private String lastName;   // User's last name
    private String message;    // Status message (success or error)
    private boolean success;   // true = registration successful, false = failed

    // Empty constructor
    public RegisterResponse() {
    }

    // Full constructor
    public RegisterResponse(String token, String email, String firstName, 
                           String lastName, String message, boolean success) {
        this.token = token;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.message = message;
        this.success = success;
    }

    // ========== GETTERS AND SETTERS ==========
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
