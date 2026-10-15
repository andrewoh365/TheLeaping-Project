package com.leaping.portfolio_app.auth.service;

import com.leaping.portfolio_app.auth.dto.AuthResponse;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.model.UserSession;
import com.leaping.portfolio_app.auth.repository.UserSessionRepository;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Server-side sign-in sessions. Access tokens stay short-lived. The session
 * itself ends at a fixed time, and logout or reuse of an old refresh token
 * revokes it immediately.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final UserSessionRepository userSessionRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public SessionService(UserSessionRepository userSessionRepository, JwtTokenProvider jwtTokenProvider) {
        this.userSessionRepository = userSessionRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public IssuedTokens openSession(User user) {
        OffsetDateTime now = now();
        UserSession session = new UserSession();
        session.setSessionId(UUID.randomUUID());
        session.setUser(user);
        session.setIssuedAt(now);
        session.setExpiresAt(now.plus(jwtTokenProvider.getRefreshTokenTtlMs(), ChronoUnit.MILLIS));
        session.setLastUsedAt(now);

        IssuedTokens tokens = issueTokens(session);
        session.setRefreshTokenHash(hashToken(tokens.refreshToken()));
        userSessionRepository.save(session);
        return tokens;
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return failure("Refresh token is required");
        }
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            return failure("Invalid or expired refresh token");
        }

        UUID sessionId = jwtTokenProvider.getSessionId(refreshToken);
        UserSession session = sessionId == null
                ? null
                : userSessionRepository.findById(sessionId).orElse(null);
        if (session == null || !isOpen(session)) {
            return failure("Invalid or expired refresh token");
        }

        User user = session.getUser();
        if (user == null || !user.isActive()) {
            return failure("User account is inactive or not found");
        }

        if (!hashesEqual(hashToken(refreshToken), session.getRefreshTokenHash())) {
            session.setRevokedAt(now());
            userSessionRepository.save(session);
            log.warn("Revoked session {} after a refresh token was reused", session.getSessionId());
            return failure("Invalid or expired refresh token");
        }

        IssuedTokens tokens = issueTokens(session);
        session.setRefreshTokenHash(hashToken(tokens.refreshToken()));
        session.setLastUsedAt(now());
        userSessionRepository.save(session);
        return new AuthResponse(
                tokens.accessToken(),
                user.getEmail(),
                "Token refreshed successfully",
                true,
                user.getRole().name(),
                tokens.refreshToken()
        );
    }

    @Transactional
    public void revokePresented(String accessToken, String refreshToken) {
        revokeToken(accessToken);
        revokeToken(refreshToken);
    }

    @Transactional
    public int revokeOtherSessions(Long userId, UUID currentSessionId) {
        List<UserSession> sessions = userSessionRepository.findByUser_IdAndRevokedAtIsNull(userId);
        OffsetDateTime revokedAt = now();
        int revoked = 0;
        for (UserSession session : sessions) {
            if (session.getSessionId().equals(currentSessionId)) {
                continue;
            }
            session.setRevokedAt(revokedAt);
            revoked++;
        }
        if (revoked > 0) {
            userSessionRepository.saveAll(sessions);
        }
        return revoked;
    }

    public boolean isAccessTokenActive(String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token) || jwtTokenProvider.isRefreshToken(token)) {
            return false;
        }
        UUID sessionId = jwtTokenProvider.getSessionId(token);
        if (sessionId == null) {
            return false;
        }
        return userSessionRepository.findById(sessionId).map(this::isOpen).orElse(false);
    }

    private void revokeToken(String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            return;
        }
        UUID sessionId = jwtTokenProvider.getSessionId(token);
        if (sessionId == null) {
            return;
        }
        userSessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(now());
                userSessionRepository.save(session);
            }
        });
    }

    private IssuedTokens issueTokens(UserSession session) {
        long sessionEndMs = session.getExpiresAt().toInstant().toEpochMilli();
        Date accessExpiresAt = new Date(Math.min(
                System.currentTimeMillis() + jwtTokenProvider.getAccessTokenTtlMs(),
                sessionEndMs
        ));
        Date refreshExpiresAt = new Date(sessionEndMs);
        UUID sessionId = session.getSessionId();
        String email = session.getUser().getEmail();
        return new IssuedTokens(
                jwtTokenProvider.generateAccessToken(email, sessionId, accessExpiresAt),
                jwtTokenProvider.generateRefreshToken(email, sessionId, refreshExpiresAt)
        );
    }

    private boolean isOpen(UserSession session) {
        return session.getRevokedAt() == null && session.getExpiresAt().isAfter(now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private AuthResponse failure(String message) {
        return new AuthResponse(null, null, message, false);
    }

    static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static boolean hashesEqual(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
