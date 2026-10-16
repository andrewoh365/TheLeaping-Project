package com.leaping.portfolio_app.admin.dto;

public class AdminClientSummaryResponse {

    private Long clientId;
    private String firstName;
    private String lastName;
    private String email;
    private String status;

    public AdminClientSummaryResponse() {
    }

    public AdminClientSummaryResponse(
            Long clientId,
            String firstName,
            String lastName,
            String email,
            String status
    ) {
        this.clientId = clientId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.status = status;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}