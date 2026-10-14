package com.leaping.portfolio_app.audit.repository;

import com.leaping.portfolio_app.audit.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository
        extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByUser_IdOrderByOccurredAtAscAuditEventIdAsc(
            Long userId,
            Pageable pageable
    );

    Page<AuditEvent> findByUser_IdOrderByOccurredAtDescAuditEventIdDesc(
            Long userId,
            Pageable pageable
    );

    Page<AuditEvent> findByOrder_OrderIdOrderByOccurredAtAscAuditEventIdAsc(
            Long orderId,
            Pageable pageable
    );
}