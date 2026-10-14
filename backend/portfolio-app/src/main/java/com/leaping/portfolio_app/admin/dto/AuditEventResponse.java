package com.leaping.portfolio_app.admin.dto;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class AuditEventResponse {

    private Long auditEventId;
    private String eventType;
    private OffsetDateTime occurredAt;
    private Long userId;
    private Long orderId;
    private Long executionAttemptId;
    private Long tradeId;
    private Long cashTransactionId;
    private Map<String, Object> eventDetails;

    public AuditEventResponse() {
    }

    public AuditEventResponse(
            Long auditEventId,
            String eventType,
            OffsetDateTime occurredAt,
            Long userId,
            Long orderId,
            Long executionAttemptId,
            Long tradeId,
            Long cashTransactionId,
            Map<String, Object> eventDetails
    ) {
        this.auditEventId = auditEventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.orderId = orderId;
        this.executionAttemptId = executionAttemptId;
        this.tradeId = tradeId;
        this.cashTransactionId = cashTransactionId;
        this.eventDetails = eventDetails == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(eventDetails);
    }

    public Long getAuditEventId() {
        return auditEventId;
    }

    public void setAuditEventId(Long auditEventId) {
        this.auditEventId = auditEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public void setExecutionAttemptId(Long executionAttemptId) {
        this.executionAttemptId = executionAttemptId;
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

    public void setCashTransactionId(Long cashTransactionId) {
        this.cashTransactionId = cashTransactionId;
    }

    public Map<String, Object> getEventDetails() {
        return eventDetails;
    }

    public void setEventDetails(Map<String, Object> eventDetails) {
        this.eventDetails = eventDetails;
    }
}