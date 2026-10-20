package com.leaping.portfolio_app.auth.service;

import com.leaping.portfolio_app.auth.dto.AuthResponse;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:sessionrace;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;LOCK_TIMEOUT=30000",
                "spring.datasource.driverClassName=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false",
                "spring.task.scheduling.enabled=false",
                "market.simulation.enabled=false",
                "market.pricing.refresh-ms=31536000000",
                "market.pricing.history-days=1",
                "market.universe.csv-path=classpath:data/closed-market-universe.csv",
                "jwt.secret=ThisIsATestOnlyJwtSecretKeyWithAtLeast64CharactersForHS512SigningAlgo",
                "jwt.expiration=3600000",
                "jwt.refresh-expiration=604800000"
        }
)
class SessionRefreshConcurrencyTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void concurrentRefresh_onlyOneSucceedsAndReuseRevokesTheSession() throws Exception {
        User user = userRepository.save(new User(
                "race-" + UUID.randomUUID() + "@example.com",
                "hashed",
                "Race",
                "User"
        ));
        IssuedTokens tokens = sessionService.openSession(user);
        String refreshToken = tokens.refreshToken();

        AtomicInteger rotations = new AtomicInteger();
        CountDownLatch firstHolding = new CountDownLatch(1);
        CountDownLatch secondAtLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        SessionService target = AopTestUtils.getTargetObject(sessionService);
        target.beforeRefreshLock = () -> {
            if (secondAtLock.getCount() == 1 && firstHolding.getCount() == 0) {
                secondAtLock.countDown();
            }
        };
        target.beforeRefreshRotation = () -> {
            if (rotations.incrementAndGet() == 1) {
                firstHolding.countDown();
                try {
                    if (!releaseFirst.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out holding the refresh transaction");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("refresh transaction was interrupted", ex);
                }
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<AuthResponse> first = pool.submit(() -> sessionService.refresh(refreshToken));
            assertTrue(firstHolding.await(20, TimeUnit.SECONDS), "first refresh did not lock the session");

            Future<AuthResponse> second = pool.submit(() -> sessionService.refresh(refreshToken));
            assertTrue(secondAtLock.await(20, TimeUnit.SECONDS), "second refresh did not reach the row lock");
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(750);
            while (System.nanoTime() < deadline && rotations.get() == 1 && !second.isDone()) {
                Thread.sleep(25);
            }
            assertEquals(1, rotations.get(), "both refreshes passed the hash check before either committed");
            assertFalse(second.isDone(), "second refresh finished while the first still held the row lock");

            releaseFirst.countDown();
            AuthResponse firstResult = first.get(20, TimeUnit.SECONDS);
            AuthResponse secondResult = second.get(20, TimeUnit.SECONDS);
            List<AuthResponse> results = List.of(firstResult, secondResult);
            long successes = results.stream().filter(AuthResponse::isSuccess).count();
            assertEquals(1, successes);
            assertEquals(1, rotations.get());

            AuthResponse winner = results.stream().filter(AuthResponse::isSuccess).findFirst().orElseThrow();
            assertFalse(sessionService.isAccessTokenActive(tokens.accessToken()));
            assertFalse(sessionService.isAccessTokenActive(winner.getToken()));
            assertFalse(sessionService.refresh(winner.getRefreshToken()).isSuccess());
            assertFalse(sessionService.refresh(refreshToken).isSuccess());
        } finally {
            releaseFirst.countDown();
            target.beforeRefreshLock = () -> { };
            target.beforeRefreshRotation = () -> { };
            pool.shutdownNow();
        }
    }
}
