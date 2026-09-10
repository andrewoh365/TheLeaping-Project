package com.leaping.portfolio_app.auth.service;

import com.leaping.portfolio_app.auth.dto.AuthResponse;
import com.leaping.portfolio_app.auth.dto.LoginRequest;
import com.leaping.portfolio_app.auth.dto.RegisterRequest;
import com.leaping.portfolio_app.auth.dto.RegisterResponse;
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
        // Return response with role included so frontend knows the user's role
        return new AuthResponse(token, email, "Authentication successful", true, user.getRole().name());
    }

    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }

    public String getEmailFromToken(String token) {
        return jwtTokenProvider.getEmailFromToken(token);
    }

    /**
     * Register a new user
     * This handles the registration flow:
     * 1. Validate all required fields are present
     * 2. Check passwords match
     * 3. Check email doesn't already exist
     * 4. Hash the password with BCrypt
     * 5. Create new User with CUSTOMER role
     * 6. Save to repository
     * 7. Generate JWT token
     * 8. Return success with token
     */
    public RegisterResponse register(RegisterRequest registerRequest) {
        // STEP 1: Validate input - check all fields are provided
        if (registerRequest.getEmail() == null || registerRequest.getEmail().trim().isEmpty()) {
            return new RegisterResponse(null, null, null, null, 
                "Email is required", false);
        }
        
        if (registerRequest.getPassword() == null || registerRequest.getPassword().trim().isEmpty()) {
            return new RegisterResponse(null, null, null, null, 
                "Password is required", false);
        }
        
        if (registerRequest.getFirstName() == null || registerRequest.getFirstName().trim().isEmpty()) {
            return new RegisterResponse(null, null, null, null, 
                "First name is required", false);
        }
        
        if (registerRequest.getLastName() == null || registerRequest.getLastName().trim().isEmpty()) {
            return new RegisterResponse(null, null, null, null, 
                "Last name is required", false);
        }
        
        // STEP 2: Validate passwords match
        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            return new RegisterResponse(null, null, null, null, 
                "Passwords do not match", false);
        }
        
        // STEP 3: Validate password strength (minimum 8 characters)
        if (registerRequest.getPassword().length() < 8) {
            return new RegisterResponse(null, null, null, null, 
                "Password must be at least 8 characters long", false);
        }
        
        // STEP 4: Check email doesn't already exist
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            return new RegisterResponse(null, null, null, null, 
                "Email already exists", false);
        }
        
        // STEP 5: Hash the password using BCrypt
        String hashedPassword = passwordEncoder.encode(registerRequest.getPassword());
        
        // STEP 6: Create new User object with encrypted password
        User newUser = new User(
            null,  // ID will be generated by repository
            registerRequest.getEmail(),
            hashedPassword,  // Use the HASHED password, not plain text
            registerRequest.getFirstName(),
            registerRequest.getLastName()
        );
        
        // STEP 7: Save the new user to the repository
        User savedUser = userRepository.save(newUser);
        
        // STEP 8: Generate JWT token for the new user
        String token = jwtTokenProvider.generateToken(savedUser.getEmail());
        
        // STEP 9: Return success response with token and user info
        return new RegisterResponse(
            token,
            savedUser.getEmail(),
            savedUser.getFirstName(),
            savedUser.getLastName(),
            "User created successfully",
            true
        );
    }

    public AuthResponse logout() {
        return new AuthResponse(null, null, "Logout successful", true);
    }
}
