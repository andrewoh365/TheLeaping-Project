package com.leaping.portfolio_app.auth.service;

import com.leaping.portfolio_app.auth.dto.AuthResponse;
import com.leaping.portfolio_app.auth.dto.LoginRequest;
import com.leaping.portfolio_app.auth.dto.RegisterRequest;
import com.leaping.portfolio_app.auth.dto.RegisterResponse;
import com.leaping.portfolio_app.auth.dto.RefreshRequest;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import com.leaping.portfolio_app.auth.repository.CustomerRepository;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, CustomerRepository customerRepository,
                      JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
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

        // Generate JWT access token and refresh token
        String token = jwtTokenProvider.generateToken(email);
        String refreshToken = jwtTokenProvider.generateRefreshToken(email);
        
        // Return response with role and refresh token included
        return new AuthResponse(token, email, "Authentication successful", true, user.getRole().name(), refreshToken);
    }

    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }

    public String getEmailFromToken(String token) {
        return jwtTokenProvider.getEmailFromToken(token);
    }

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

        if (registerRequest.getDateOfBirth() == null || registerRequest.getDateOfBirth().trim().isEmpty()) {
            return new RegisterResponse(null, null, null, null, 
                "Date of birth is required", false);
        }

        if (registerRequest.getTaxId() == null || registerRequest.getTaxId().trim().isEmpty()) {
            return new RegisterResponse(null, null, null, null, 
                "Tax ID is required", false);
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

        // STEP 5: Check tax ID doesn't already exist (now in CustomerRepository)
        if (customerRepository.existsByTaxId(registerRequest.getTaxId())) {
            return new RegisterResponse(null, null, null, null, 
                "Tax ID already exists", false);
        }
        
        // STEP 6: Hash the password using BCrypt
        String hashedPassword = passwordEncoder.encode(registerRequest.getPassword());
        
        // STEP 7: Parse date of birth
        java.time.LocalDate dateOfBirth;
        try {
            dateOfBirth = java.time.LocalDate.parse(registerRequest.getDateOfBirth());
        } catch (java.time.format.DateTimeParseException e) {
            return new RegisterResponse(null, null, null, null, 
                "Invalid date format. Use YYYY-MM-DD", false);
        }
        
        // STEP 8: Create new User object (without dateOfBirth/taxId - those go in Customer)
        User newUser = new User(
            registerRequest.getEmail(),
            hashedPassword,  // Use the HASHED password, not plain text
            registerRequest.getFirstName(),
            registerRequest.getLastName()
        );
        
        // STEP 9: Save the new user to the database
        User savedUser = userRepository.save(newUser);
        
        // STEP 10: Create Customer record linked to the User via user_id
        // NOTE: With @MapsId, don't manually set userId. Let Hibernate derive it from User relationship
        Customer newCustomer = new Customer();
        newCustomer.setUser(savedUser);  // @MapsId will automatically set user_id from savedUser.id
        newCustomer.setDateOfBirth(dateOfBirth);
        newCustomer.setTaxId(registerRequest.getTaxId());
        customerRepository.save(newCustomer);
        
        // STEP 11: Generate JWT access token and refresh token for the new user
        String token = jwtTokenProvider.generateToken(savedUser.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser.getEmail());
        
        // STEP 12: Return success response with token, refresh token, and user info
        return new RegisterResponse(
            token,
            savedUser.getEmail(),
            savedUser.getFirstName(),
            savedUser.getLastName(),
            "User created successfully",
            true,
            refreshToken
        );
    }

    public AuthResponse refreshToken(RefreshRequest refreshRequest) {
        // Validate refresh token is provided
        if (refreshRequest == null || refreshRequest.getRefreshToken() == null || 
            refreshRequest.getRefreshToken().trim().isEmpty()) {
            return new AuthResponse(null, null, "Refresh token is required", false);
        }

        String refreshToken = refreshRequest.getRefreshToken();
        
        // Validate refresh token
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            return new AuthResponse(null, null, "Invalid or expired refresh token", false);
        }

        // Extract email from refresh token
        String email = jwtTokenProvider.getEmailFromRefreshToken(refreshToken);
        
        // Verify user still exists and is active
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isActive()) {
            return new AuthResponse(null, null, "User account is inactive or not found", false);
        }

        // Generate new access token and optionally new refresh token
        String newAccessToken = jwtTokenProvider.generateToken(email);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);
        
        // Return response with new tokens
        return new AuthResponse(newAccessToken, email, "Token refreshed successfully", true, 
                               user.getRole().name(), newRefreshToken);
    }

    public AuthResponse logout() {
        return new AuthResponse(null, null, "Logout successful", true);
    }
}
