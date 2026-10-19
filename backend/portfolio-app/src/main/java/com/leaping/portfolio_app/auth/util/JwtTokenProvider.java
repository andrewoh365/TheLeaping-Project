package com.leaping.portfolio_app.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:mySecretKeyForJWTTokenGenerationAndValidationPurpose123}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}")  // 1 hour for access token
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration:604800000}")  // 7 days for refresh token
    private long refreshTokenExpirationMs;

    public long getAccessTokenTtlMs() {
        return jwtExpirationMs;
    }

    public long getRefreshTokenTtlMs() {
        return refreshTokenExpirationMs;
    }

    public String generateAccessToken(String email, UUID sessionId, Date expiresAt) {
        return buildToken(email, sessionId, "access", expiresAt);
    }

    public String generateRefreshToken(String email, UUID sessionId, Date expiresAt) {
        return buildToken(email, sessionId, "refresh", expiresAt);
    }

    private String buildToken(String email, UUID sessionId, String type, Date expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(expiresAt)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public UUID getSessionId(String token) {
        try {
            Object sessionId = parseClaims(token).get("sid");
            if (sessionId == null) {
                return null;
            }
            return UUID.fromString(sessionId.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public Date getExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    public boolean isRefreshToken(String token) {
        try {
            return "refresh".equals(parseClaims(token).get("type"));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "refresh".equals(claims.get("type")) && claims.get("sid") != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public String getEmailFromRefreshToken(String token) {
        return parseClaims(token).getSubject();
    }

    private Claims parseClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
