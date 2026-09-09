package com.leaping.portfolio_app.auth.service;

import com.leaping.portfolio_app.auth.dto.AuthResponse;
import com.leaping.portfolio_app.auth.dto.LoginRequest;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse authenticate(LoginRequest loginRequest) {
        String email = loginRequest.getEmail();
        String password = loginRequest.getPassword();

        // Validate input
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return new AuthResponse(null, null, "Email and password are required", false);
        }

        // Find user by email
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return new AuthResponse(null, null, "Invalid email or password", false);
        }

        // Check if user is active
        if (!user.isActive()) {
            return new AuthResponse(null, null, "User account is inactive", false);
        }

        // Validate password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return new AuthResponse(null, null, "Invalid email or password", false);
        }

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(email);
        return new AuthResponse(token, email, "Authentication successful", true);
    }

    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }

    public String getEmailFromToken(String token) {
        return jwtTokenProvider.getEmailFromToken(token);
    }

    public AuthResponse logout() {
        return new AuthResponse(null, null, "Logout successful", true);
    }
}
