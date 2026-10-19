package com.leaping.portfolio_app.trade.entity;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.trade.enums.OrderAction;
import com.leaping.portfolio_app.trade.enums.OrderStatus;
import com.leaping.portfolio_app.trade.enums.OrderType;
import com.leaping.portfolio_app.trade.enums.TimeInForce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A client's order and the firm's permanent record of it (BR-14).
 *
 * The order terms (who, what, side, type, quantity, price, when submitted) are written
 * once and never updated. Only the lifecycle fields change, and only through the
 * transitions OrderStatus allows; see accept/fill/reject/cancel.
 */
@Entity
@Table(name = "trade_orders")
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false, updatable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false, updatable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_action", nullable = false, updatable = false, length = 10)
    private OrderAction orderAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, updatable = false, length = 10)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_in_force", nullable = false, updatable = false, length = 10)
    private TimeInForce timeInForce;

    @Column(
        name = "quantity",
        nullable = false,
        updatable = false,
        precision = 30,
        scale = 10
    )
    private BigDecimal quantity;

    @Column(
        name = "limit_price",
        updatable = false,
        precision = 20,
        scale = 8
    )
    private BigDecimal limitPriceUsd;

    /** Price quoted when the order was submitted (ask for BUY, bid for SELL): what the pre-trade checks used. */
    @Column(
        name = "reference_price_usd",
        updatable = false,
        precision = 20,
        scale = 8
    )
    private BigDecimal referencePriceUsd;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "filled_at")
    private OffsetDateTime filledAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public TradeOrder() {
    }

    public TradeOrder(
            Portfolio portfolio,
            Instrument instrument,
            OrderAction orderAction,
            OrderType orderType,
            TimeInForce timeInForce,
            BigDecimal quantity,
            BigDecimal limitPriceUsd
    ) {
        this.portfolio = portfolio;
        this.instrument = instrument;
        this.orderAction = orderAction;
        this.orderType = orderType;
        this.timeInForce = timeInForce;
        this.quantity = quantity;
        this.limitPriceUsd = limitPriceUsd;
        this.orderStatus = OrderStatus.SUBMITTED;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();

        if (submittedAt == null) {
            submittedAt = now;
        }

        if (orderStatus == null) {
            orderStatus = OrderStatus.SUBMITTED;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Passed pre-trade checks: record the firm commitment to execute. */
    public void accept() {
        transitionTo(OrderStatus.ACCEPTED);
        acceptedAt = OffsetDateTime.now();
    }

    public void fill() {
        transitionTo(OrderStatus.FILLED);
        filledAt = OffsetDateTime.now();
    }

    public void reject(String reason) {
        transitionTo(OrderStatus.REJECTED);
        rejectedAt = OffsetDateTime.now();
        rejectionReason = reason;
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
        cancelledAt = OffsetDateTime.now();
    }

    private void transitionTo(OrderStatus next) {
        if (!orderStatus.canTransitionTo(next)) {
            throw new IllegalStateException(
                String.format("Order %s cannot move from %s to %s", orderId, orderStatus, next));
        }
        orderStatus = next;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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

    public OrderAction getOrderAction() {
        return orderAction;
    }

    public void setOrderAction(OrderAction orderAction) {
        this.orderAction = orderAction;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public TimeInForce getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(TimeInForce timeInForce) {
        this.timeInForce = timeInForce;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLimitPriceUsd() {
        return limitPriceUsd;
    }

    public void setLimitPriceUsd(BigDecimal limitPriceUsd) {
        this.limitPriceUsd = limitPriceUsd;
    }

    public BigDecimal getReferencePriceUsd() {
        return referencePriceUsd;
    }

    public void setReferencePriceUsd(BigDecimal referencePriceUsd) {
        this.referencePriceUsd = referencePriceUsd;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(OffsetDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public OffsetDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(OffsetDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(OffsetDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public OffsetDateTime getFilledAt() {
        return filledAt;
    }

    public void setFilledAt(OffsetDateTime filledAt) {
        this.filledAt = filledAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}