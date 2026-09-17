package com.leaping.portfolio_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_event_id")
    private Long auditEventId;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "execution_attempt_id")
    private Long executionAttemptId;

    @Column(name = "trade_id")
    private Long tradeId;

    @Column(name = "cash_transaction_id")
    private Long cashTransactionId;

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
        insertable = false,
        updatable = false
    )
    private OffsetDateTime occurredAt;

    public AuditEvent() {
    }

    public Long getAuditEventId() {
        return auditEventId;
    }

    public void setAuditEventId(Long auditEventId) {
        this.auditEventId = auditEventId;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getExecutionAttemptId() {
        return executionAttemptId;
    }

    public void setExecutionAttemptId(
            Long executionAttemptId) {

        this.executionAttemptId =
                executionAttemptId;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public Long getCashTransactionId() {
        return cashTransactionId;
    }

    public void setCashTransactionId(
            Long cashTransactionId) {

        this.cashTransactionId =
                cashTransactionId;
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
            Map<String, Object> eventDetails) {

        this.eventDetails = eventDetails;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}