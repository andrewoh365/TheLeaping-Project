package com.leaping.portfolio_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "holding")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holding_id")
    private Long holdingId;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(
        name = "quantity",
        nullable = false,
        precision = 30,
        scale = 10
    )
    private BigDecimal quantity;

    @Column(
        name = "average_cost_usd",
        nullable = false,
        precision = 20,
        scale = 8
    )
    private BigDecimal averageCostUsd = BigDecimal.ZERO;

    @Column(
        name = "updated_at",
        insertable = false,
        updatable = false
    )
    private OffsetDateTime updatedAt;

    public Holding() {
    }

    public Long getHoldingId() {
        return holdingId;
    }

    public void setHoldingId(Long holdingId) {
        this.holdingId = holdingId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAverageCostUsd() {
        return averageCostUsd;
    }

    public void setAverageCostUsd(BigDecimal averageCostUsd) {
        this.averageCostUsd = averageCostUsd;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}