package com.leaping.portfolio_app.auth.repository;

import com.leaping.portfolio_app.auth.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUser_IdAndRevokedAtIsNull(Long userId);
}
