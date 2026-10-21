package com.leaping.portfolio_app.audit.service;

import com.leaping.portfolio_app.audit.entity.AuditEvent;
import com.leaping.portfolio_app.audit.enums.AuditEventType;
import com.leaping.portfolio_app.audit.repository.AuditEventRepository;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.portfolio.entity.CashTransaction;
import com.leaping.portfolio_app.trade.entity.ExecutionAttempt;
import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditEvent recordEvent(
            AuditEventType eventType,
            User user,
            TradeOrder order,
            ExecutionAttempt executionAttempt,
            Trade trade,
            CashTransaction cashTransaction,
            Map<String, Object> eventDetails
    ) {
        Objects.requireNonNull(eventType, "eventType must not be null");

        Map<String, Object> copiedDetails = copyAndValidateDetails(
                eventDetails
        );

        AuditEvent auditEvent = new AuditEvent(
                user,
                order,
                executionAttempt,
                trade,
                cashTransaction,
                eventType.name(),
                copiedDetails
        );

        return auditEventRepository.save(auditEvent);
    }

    private Map<String, Object> copyAndValidateDetails(
            Map<String, Object> eventDetails
    ) {
        Map<String, Object> copiedDetails = eventDetails == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(eventDetails);

        for (String key : copiedDetails.keySet()) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(
                        "event detail keys must not be blank"
                );
            }

            String normalizedKey = key.toLowerCase(Locale.ROOT);
            if (normalizedKey.contains("password")
                    || normalizedKey.contains("secret")
                    || normalizedKey.contains("token")
                    || normalizedKey.contains("credential")
                    || normalizedKey.contains("authorization")) {
                throw new IllegalArgumentException(
                        "sensitive values must not be recorded in audit details"
                );
            }
        }

        copiedDetails.replaceAll((key, value) ->
                value instanceof TemporalAccessor ? value.toString() : value
        );

        return copiedDetails;
    }
}