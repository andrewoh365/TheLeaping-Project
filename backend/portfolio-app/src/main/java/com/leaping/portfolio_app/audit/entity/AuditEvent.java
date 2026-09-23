package com.leaping.portfolio_app.audit.entity;

import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.portfolio.entity.CashTransaction;
import com.leaping.portfolio_app.trade.entity.ExecutionAttempt;
import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.trade.entity.TradeOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_event_id")
    private Long auditEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private TradeOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_attempt_id")
    private ExecutionAttempt executionAttempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id")
    private Trade trade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_transaction_id")
    private CashTransaction cashTransaction;

    @Column(
        name = "event_type",
        nullable = false,
        length = 100
    )
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
        name = "event_details",
        nullable = false,
        columnDefinition = "jsonb"
    )
    private Map<String, Object> eventDetails =
            new HashMap<>();

    @Column(
        name = "occurred_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    private OffsetDateTime occurredAt;

    public AuditEvent() {
    }

    public AuditEvent(
            User user,
            TradeOrder order,
            ExecutionAttempt executionAttempt,
            Trade trade,
            CashTransaction cashTransaction,
            String eventType,
            Map<String, Object> eventDetails
    ) {
        this.user = user;
        this.order = order;
        this.executionAttempt = executionAttempt;
        this.trade = trade;
        this.cashTransaction = cashTransaction;
        this.eventType = eventType;

        if (eventDetails != null) {
            this.eventDetails = eventDetails;
        }
    }

    public Long getAuditEventId() {
        return auditEventId;
    }

    public void setAuditEventId(Long auditEventId) {
        this.auditEventId = auditEventId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TradeOrder getOrder() {
        return order;
    }

    public void setOrder(TradeOrder order) {
        this.order = order;
    }

    public ExecutionAttempt getExecutionAttempt() {
        return executionAttempt;
    }

    public void setExecutionAttempt(
            ExecutionAttempt executionAttempt
    ) {
        this.executionAttempt = executionAttempt;
    }

    public Trade getTrade() {
        return trade;
    }

    public void setTrade(Trade trade) {
        this.trade = trade;
    }

    public CashTransaction getCashTransaction() {
        return cashTransaction;
    }

    public void setCashTransaction(
            CashTransaction cashTransaction
    ) {
        this.cashTransaction = cashTransaction;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Map<String, Object> getEventDetails() {
        return eventDetails;
    }

    public void setEventDetails(
            Map<String, Object> eventDetails
    ) {
        this.eventDetails = eventDetails;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}