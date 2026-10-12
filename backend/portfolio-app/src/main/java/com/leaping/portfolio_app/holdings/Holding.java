package com.leaping.portfolio_app.holdings;

import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.instrument.entity.Instrument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "holdings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_holding_portfolio_instrument",
            columnNames = {"portfolio_id", "instrument_id"}
        )
    }
)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holding_id")
    private Long holdingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

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

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Holding() {
    }

    public Holding(
            Portfolio portfolio,
            Instrument instrument,
            BigDecimal quantity,
            BigDecimal averageCostUsd
    ) {
        this.portfolio = portfolio;
        this.instrument = instrument;
        this.quantity = quantity;
        this.averageCostUsd = averageCostUsd;
    }

    @PrePersist
    protected void onCreate() {
        if (averageCostUsd == null) {
            averageCostUsd = BigDecimal.ZERO;
        }

        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getHoldingId() {
        return holdingId;
    }

    public void setHoldingId(Long holdingId) {
        this.holdingId = holdingId;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
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

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
