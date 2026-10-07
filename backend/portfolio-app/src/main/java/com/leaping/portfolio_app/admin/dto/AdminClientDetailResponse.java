package com.leaping.portfolio_app.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AdminClientDetailResponse {

    private Long clientId;
    private String firstName;
    private String lastName;
    private String email;
    private String status;

    private OffsetDateTime createdAt;
    private OffsetDateTime lastLogin;

    private Long portfolioId;
    private BigDecimal cashBalanceUsd;

    public AdminClientDetailResponse() {
    }

    public AdminClientDetailResponse(
            Long clientId,
            String firstName,
            String lastName,
            String email,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime lastLogin,
            Long portfolioId,
            BigDecimal cashBalanceUsd
    ) {
        this.clientId = clientId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
        this.lastLogin = lastLogin;
        this.portfolioId = portfolioId;
        this.cashBalanceUsd = cashBalanceUsd;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(OffsetDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public BigDecimal getCashBalanceUsd() {
        return cashBalanceUsd;
    }

    public void setCashBalanceUsd(BigDecimal cashBalanceUsd) {
        this.cashBalanceUsd = cashBalanceUsd;
    }
}