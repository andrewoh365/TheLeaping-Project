package com.leaping.portfolio_app.auth.repository;

import com.leaping.portfolio_app.auth.model.UserSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUser_IdAndRevokedAtIsNull(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UserSession session where session.sessionId = :sessionId")
    Optional<UserSession> lockById(@Param("sessionId") UUID sessionId);
}
