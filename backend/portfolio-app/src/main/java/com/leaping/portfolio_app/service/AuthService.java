package com.leaping.portfolio_app.service;

import com.leaping.portfolio_app.dto.*;
import com.leaping.portfolio_app.entity.client;
import com.leaping.portfolio_app.repository.ClientRepository;
import com.leaping.portfolio_app.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(ClientRepository clientRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (clientRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Create new client
        client client_obj = new client();
        client_obj.setEmail(request.getEmail());
        client_obj.setPassword(passwordEncoder.encode(request.getPassword()));

        client savedUser = clientRepository.save(client_obj);

        // Generate tokens
        String accessToken = jwtProvider.generateToken(String.valueOf(savedUser.getId()), savedUser.getEmail(), "");
        String refreshToken = jwtProvider.generateRefreshToken(String.valueOf(savedUser.getId()));

        return new AuthResponse(
                accessToken,
                refreshToken,
                3600L,
                new UserDto(String.valueOf(savedUser.getId()), savedUser.getEmail())
        );
    }

    public AuthResponse login(LoginRequest request) {
        client user = clientRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        // Generate tokens
        String accessToken = jwtProvider.generateToken(String.valueOf(user.getId()), user.getEmail(), "");
        String refreshToken = jwtProvider.generateRefreshToken(String.valueOf(user.getId()));

        return new AuthResponse(
                accessToken,
                refreshToken,
                3600L,
                new UserDto(String.valueOf(user.getId()), user.getEmail())
        );
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtProvider.validateToken(request.getRefreshToken())) {
            throw new RuntimeException("Invalid refresh token");
        }

        String userId = jwtProvider.getUserIdFromToken(request.getRefreshToken());
        client user = clientRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        String accessToken = jwtProvider.generateToken(String.valueOf(user.getId()), user.getEmail(), "");
        String newRefreshToken = jwtProvider.generateRefreshToken(String.valueOf(user.getId()));

        return new AuthResponse(
                accessToken,
                newRefreshToken,
                3600L,
                new UserDto(String.valueOf(user.getId()), user.getEmail())
        );
    }

    public Optional<client> getCurrentUser(String token) {
        if (!jwtProvider.validateToken(token)) {
            return Optional.empty();
        }

        String userId = jwtProvider.getUserIdFromToken(token);
        return clientRepository.findById(Long.parseLong(userId));
    }
}
