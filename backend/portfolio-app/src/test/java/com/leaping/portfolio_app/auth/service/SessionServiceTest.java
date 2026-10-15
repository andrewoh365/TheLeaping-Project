package com.leaping.portfolio_app.auth.service;

import com.leaping.portfolio_app.auth.dto.AuthResponse;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.model.UserSession;
import com.leaping.portfolio_app.auth.repository.UserSessionRepository;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final long ACCESS_TTL_MS = 3_600_000L;
    private static final long REFRESH_TTL_MS = 604_800_000L;

    @Mock
    private UserSessionRepository userSessionRepository;

    private final Map<UUID, UserSession> sessions = new HashMap<>();
    private JwtTokenProvider jwtTokenProvider;
    private SessionService sessionService;
    private User user;

    @BeforeEach
    void setUp() {
        sessions.clear();
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", "x".repeat(80));
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", ACCESS_TTL_MS);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpirationMs", REFRESH_TTL_MS);

        lenient().when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> {
            UserSession session = invocation.getArgument(0);
            sessions.put(session.getSessionId(), session);
            return session;
        });
        lenient().when(userSessionRepository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(sessions.get(invocation.getArgument(0)))
        );
        lenient().when(userSessionRepository.lockById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(sessions.get(invocation.getArgument(0)))
        );
        lenient().when(userSessionRepository.findByUser_IdAndRevokedAtIsNull(any(Long.class))).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            List<UserSession> open = new ArrayList<>();
            for (UserSession session : sessions.values()) {
                if (session.getUser().getId().equals(userId) && session.getRevokedAt() == null) {
                    open.add(session);
                }
            }
            return open;
        });
        lenient().when(userSessionRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<UserSession> batch = invocation.getArgument(0);
            List<UserSession> saved = new ArrayList<>();
            for (UserSession session : batch) {
                sessions.put(session.getSessionId(), session);
                saved.add(session);
            }
            return saved;
        });

        sessionService = new SessionService(userSessionRepository, jwtTokenProvider);
        user = new User("ada@example.com", "hashed", "Ada", "Lovelace");
        user.setId(5L);
    }

    @Test
    void openSession_boundsTheAccessTokenAndStoresOnlyAHash() {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
        IssuedTokens tokens = sessionService.openSession(user);

        long accessLifetime = jwtTokenProvider.getExpiration(tokens.accessToken()).getTime() - System.currentTimeMillis();
        long refreshLifetime = jwtTokenProvider.getExpiration(tokens.refreshToken()).getTime() - System.currentTimeMillis();
        assertTrue(accessLifetime > 0 && accessLifetime <= ACCESS_TTL_MS);
        assertTrue(refreshLifetime > ACCESS_TTL_MS && refreshLifetime <= REFRESH_TTL_MS);
        assertFalse(jwtTokenProvider.isRefreshToken(tokens.accessToken()));
        assertTrue(jwtTokenProvider.isRefreshToken(tokens.refreshToken()));

        UserSession stored = sessions.get(jwtTokenProvider.getSessionId(tokens.accessToken()));
        assertEquals(SessionService.hashToken(tokens.refreshToken()), stored.getRefreshTokenHash());
        assertNotEquals(tokens.refreshToken(), stored.getRefreshTokenHash());
        assertTrue(stored.getExpiresAt().isAfter(before.plus(6, ChronoUnit.DAYS)));
        assertTrue(sessionService.isAccessTokenActive(tokens.accessToken()));
        assertFalse(sessionService.isAccessTokenActive(tokens.refreshToken()));
    }

    @Test
    void refresh_rotatesTheTokenAndRejectsReuseByRevokingTheSession() {
        IssuedTokens original = sessionService.openSession(user);

        AuthResponse refreshed = sessionService.refresh(original.refreshToken());
        assertTrue(refreshed.isSuccess());
        assertNotEquals(original.refreshToken(), refreshed.getRefreshToken());
        assertTrue(sessionService.isAccessTokenActive(original.accessToken()));
        assertTrue(sessionService.isAccessTokenActive(refreshed.getToken()));

        AuthResponse reused = sessionService.refresh(original.refreshToken());
        assertFalse(reused.isSuccess());
        assertFalse(sessionService.isAccessTokenActive(original.accessToken()));
        assertFalse(sessionService.isAccessTokenActive(refreshed.getToken()));
        assertFalse(sessionService.refresh(refreshed.getRefreshToken()).isSuccess());
    }

    @Test
    void logout_revokesTheSessionBeforeTheAccessTokenExpires() {
        IssuedTokens tokens = sessionService.openSession(user);

        sessionService.revokePresented(tokens.accessToken(), null);

        assertFalse(sessionService.isAccessTokenActive(tokens.accessToken()));
        assertFalse(sessionService.refresh(tokens.refreshToken()).isSuccess());
    }

    @Test
    void expiredSession_rejectsRefreshAndAccess() {
        IssuedTokens tokens = sessionService.openSession(user);
        UserSession stored = sessions.get(jwtTokenProvider.getSessionId(tokens.accessToken()));
        stored.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));

        assertFalse(sessionService.isAccessTokenActive(tokens.accessToken()));
        assertFalse(sessionService.refresh(tokens.refreshToken()).isSuccess());
    }

    @Test
    void revokeOtherSessions_leavesOnlyTheCurrentSession() {
        IssuedTokens current = sessionService.openSession(user);
        IssuedTokens other = sessionService.openSession(user);

        int revoked = sessionService.revokeOtherSessions(
                user.getId(),
                jwtTokenProvider.getSessionId(current.accessToken())
        );

        assertEquals(1, revoked);
        assertTrue(sessionService.isAccessTokenActive(current.accessToken()));
        assertFalse(sessionService.isAccessTokenActive(other.accessToken()));
    }
}
