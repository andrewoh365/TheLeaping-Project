package com.leaping.portfolio_app.trade.repository;

import com.leaping.portfolio_app.trade.entity.ExecutionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExecutionAttemptRepository extends JpaRepository<ExecutionAttempt, Long> {
    Optional<ExecutionAttempt> findByOrderOrderId(Long orderId);
    List<ExecutionAttempt> findByOrderOrderIdOrderByAttemptNumberDesc(Long orderId);
}
