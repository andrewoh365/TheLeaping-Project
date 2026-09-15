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
 *   "lastName": "Doe",
 *   "dateOfBirth": "1990-05-15",
 *   "taxId": "123456789"
 * }
 */
public class RegisterRequest {
    private String email;
    private String password;
    private String confirmPassword;  // User types password twice to catch typos
    private String firstName;
    private String lastName;
    private String dateOfBirth;      // Format: YYYY-MM-DD
    private String taxId;            // Unique tax identifier

    // Empty constructor (Spring needs this for JSON parsing)
    public RegisterRequest() {
    }

    // Constructor with all fields
    public RegisterRequest(String email, String password, String confirmPassword, 
                          String firstName, String lastName, String dateOfBirth, String taxId) {
        this.email = email;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.taxId = taxId;
    }

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

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }
}
