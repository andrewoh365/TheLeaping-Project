package com.leaping.portfolio_app.trade.repository;

import com.leaping.portfolio_app.trade.entity.ExecutionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionAttemptRepository extends JpaRepository<ExecutionAttempt, Long> {
    List<ExecutionAttempt> findByOrderOrderIdOrderByAttemptNumberDesc(Long orderId);

    long countByOrderOrderId(Long orderId);
}
