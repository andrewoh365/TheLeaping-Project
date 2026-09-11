package com.leaping.portfolio_app.service;

import com.leaping.portfolio_app.dto.*;
import com.leaping.portfolio_app.entity.User;
import com.leaping.portfolio_app.repository.UserRepository;
import com.leaping.portfolio_app.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

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
