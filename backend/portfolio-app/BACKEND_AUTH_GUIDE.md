# Backend Authentication API Implementation Guide

## Overview
This guide provides specifications for implementing JWT authentication endpoints in the Spring Boot backend to support the Angular frontend.

## Technology Stack
- Spring Boot 4.1.1
- Spring Security
- JWT (JSON Web Tokens)
- Spring Data JPA
- Database: (Configure as needed)

## Required Dependencies

Add these to your `pom.xml`:

```xml
<!-- JWT Support -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>

<!-- Lombok (optional, for reducing boilerplate) -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

## Implementation Steps

### 1. Create User Entity

```java
package com.leaping.portfolio_app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = System.currentTimeMillis();
    }
}
```

### 2. Create User Repository

```java
package com.leaping.portfolio_app.repository;

import com.leaping.portfolio_app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
```

### 3. Create JWT Utility Class

```java
package com.leaping.portfolio_app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtProvider {
    
    @Value("${jwt.secret:my-secret-key-for-jwt-token-signing-very-long-key}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}")
    private int jwtExpirationInMs;

    @Value("${jwt.refresh-expiration:86400000}")
    private int refreshTokenExpirationInMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", userDetails.getUsername());
        return createToken(claims, userDetails.getUsername(), jwtExpirationInMs);
    }

    public String generateToken(String userId, String email, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("username", username);
        claims.put("userId", userId);
        return createToken(claims, email, jwtExpirationInMs);
    }

    public String generateRefreshToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("userId", userId);
        return createToken(claims, userId, refreshTokenExpirationInMs);
    }

    private String createToken(Map<String, Object> claims, String subject, int expirationTimeInMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTimeInMs);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUserIdFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return (String) claims.get("userId");
    }

    public String getEmailFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    private Date getExpirationDateFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.getExpiration();
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
```

### 4. Create Request/Response DTOs

```java
// LoginRequest.java
package com.leaping.portfolio_app.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}

// RegisterRequest.java
package com.leaping.portfolio_app.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String email;
    private String username;
    private String password;
}

// AuthResponse.java
package com.leaping.portfolio_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private Long expiresIn;
    private UserDto user;
}

// RefreshTokenRequest.java
package com.leaping.portfolio_app.dto;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}

// UserDto.java
package com.leaping.portfolio_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserDto {
    private String id;
    private String email;
    private String username;
}
```

### 5. Create Authentication Service

```java
package com.leaping.portfolio_app.service;

import com.leaping.portfolio_app.dto.*;
import com.leaping.portfolio_app.entity.User;
import com.leaping.portfolio_app.repository.UserRepository;
import com.leaping.portfolio_app.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
        }

        // Create new user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);

        // Generate tokens
        String accessToken = jwtProvider.generateToken(savedUser.getId(), savedUser.getEmail(), savedUser.getUsername());
        String refreshToken = jwtProvider.generateRefreshToken(savedUser.getId());

        return new AuthResponse(
                accessToken,
                refreshToken,
                3600L,
                new UserDto(savedUser.getId(), savedUser.getEmail(), savedUser.getUsername())
        );
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        // Generate tokens
        String accessToken = jwtProvider.generateToken(user.getId(), user.getEmail(), user.getUsername());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        return new AuthResponse(
                accessToken,
                refreshToken,
                3600L,
                new UserDto(user.getId(), user.getEmail(), user.getUsername())
        );
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtProvider.validateToken(request.getRefreshToken())) {
            throw new RuntimeException("Invalid refresh token");
        }

        String userId = jwtProvider.getUserIdFromToken(request.getRefreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String accessToken = jwtProvider.generateToken(user.getId(), user.getEmail(), user.getUsername());
        String newRefreshToken = jwtProvider.generateRefreshToken(user.getId());

        return new AuthResponse(
                accessToken,
                newRefreshToken,
                3600L,
                new UserDto(user.getId(), user.getEmail(), user.getUsername())
        );
    }

    public Optional<User> getCurrentUser(String token) {
        if (!jwtProvider.validateToken(token)) {
            return Optional.empty();
        }

        String userId = jwtProvider.getUserIdFromToken(token);
        return userRepository.findById(userId);
    }
}
```

### 6. Create Authentication Controller

```java
package com.leaping.portfolio_app.controller;

import com.leaping.portfolio_app.dto.*;
import com.leaping.portfolio_app.entity.User;
import com.leaping.portfolio_app.service.AuthService;
import com.leaping.portfolio_app.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {
    private final AuthService authService;
    private final JwtProvider jwtProvider;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> user = authService.getCurrentUser(token);
            
            if (user.isPresent()) {
                User u = user.get();
                return ResponseEntity.ok(new UserDto(u.getId(), u.getEmail(), u.getUsername()));
            }
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }
}
```

### 7. Configure application.yaml

```yaml
spring:
  application:
    name: portfolio-app
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  datasource:
    url: jdbc:mysql://localhost:3306/leaping_db
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver

jwt:
  secret: your-very-long-secret-key-at-least-256-bits-for-hs512-algorithm
  expiration: 3600000  # 1 hour in milliseconds
  refresh-expiration: 86400000  # 24 hours in milliseconds

server:
  port: 8080
```

### 8. Configure Security Configuration

```java
package com.leaping.portfolio_app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:4200", "http://localhost:3000")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
            }
        };
    }
}
```

## Database Migration (Flyway)

Create `src/main/resources/db/migration/V1__create_users_table.sql`:

```sql
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at BIGINT,
    updated_at BIGINT,
    CONSTRAINT uk_email UNIQUE (email),
    CONSTRAINT uk_username UNIQUE (username)
);

CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_username ON users(username);
```

## Testing the API

### 1. Register a new user
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "username": "testuser",
    "password": "password123"
  }'
```

### 2. Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

### 3. Get current user
```bash
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <your-token>"
```

### 4. Refresh token
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<your-refresh-token>"
  }'
```

## Security Best Practices

1. **Use HTTPS** in production
2. **Secure JWT Secret**: Use a strong, randomly generated secret key (at least 256 bits)
3. **Token Expiration**: Keep access token expiration short (1 hour) and refresh token longer (24 hours)
4. **HttpOnly Cookies**: Consider storing tokens in HttpOnly cookies instead of localStorage
5. **CSRF Protection**: Implement CSRF tokens for state-changing operations
6. **Rate Limiting**: Add rate limiting to authentication endpoints
7. **Password Security**: Use bcrypt or similar strong password encoding

## Support

For issues or questions about backend implementation, refer to:
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [JWT.io](https://jwt.io)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
