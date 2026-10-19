package com.leaping.portfolio_app.auth.service;

import com.leaping.portfolio_app.auth.dto.AuthResponse;
import com.leaping.portfolio_app.auth.dto.LoginRequest;
import com.leaping.portfolio_app.auth.dto.RegisterRequest;
import com.leaping.portfolio_app.auth.dto.RegisterResponse;
import com.leaping.portfolio_app.auth.dto.RefreshRequest;
import com.leaping.portfolio_app.auth.dto.UserDto;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import com.leaping.portfolio_app.auth.repository.CustomerRepository;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

// Portfolio repository for managing portfolio entities
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;



@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final PortfolioRepository portfolioRepository;
    private final SessionService sessionService;
    
    public AuthService(UserRepository userRepository, CustomerRepository customerRepository,
                      JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder, 
                      PortfolioRepository portfolioRepository, SessionService sessionService) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.portfolioRepository = portfolioRepository;
        this.sessionService = sessionService;
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

        IssuedTokens tokens = sessionService.openSession(user);
        return new AuthResponse(
                tokens.accessToken(),
                email,
                "Authentication successful",
                true,
                user.getRole().name(),
                tokens.refreshToken()
        );
    }

    public boolean validateToken(String token) {
        return sessionService.isAccessTokenActive(token);
    }

    public String getEmailFromToken(String token) {
        return jwtTokenProvider.getEmailFromToken(token);
    }
    
    // Implemented this to get ID from same table containing email {User}
    public Long getUserIdByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow( () -> new RuntimeException("User Id not found for: " + email) );
        return user.getId(); 
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

        //Step 10.5: Create a Portfolio for the new customer, starting balance $0
        Portfolio newPortfolio = new Portfolio(newCustomer);
        portfolioRepository.save(newPortfolio);
        
        IssuedTokens tokens = sessionService.openSession(savedUser);

        return new RegisterResponse(
            tokens.accessToken(),
            savedUser.getEmail(),
            savedUser.getFirstName(),
            savedUser.getLastName(),
            "User created successfully",
            true,
            tokens.refreshToken()
        );
    }

    public AuthResponse refreshToken(RefreshRequest refreshRequest) {
        String refreshToken = refreshRequest == null ? null : refreshRequest.getRefreshToken();
        return sessionService.refresh(refreshToken);
    }

    public AuthResponse logout(String accessToken, String refreshToken) {
        sessionService.revokePresented(accessToken, refreshToken);
        return new AuthResponse(null, null, "Logout successful", true);
    }

    public AuthResponse revokeOtherSessions(String accessToken) {
        if (!sessionService.isAccessTokenActive(accessToken)) {
            return new AuthResponse(null, null, "Invalid or expired token", false);
        }
        UUID sessionId = jwtTokenProvider.getSessionId(accessToken);
        String email = jwtTokenProvider.getEmailFromToken(accessToken);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || sessionId == null) {
            return new AuthResponse(null, null, "Invalid or expired token", false);
        }
        sessionService.revokeOtherSessions(user.getId(), sessionId);
        return new AuthResponse(null, email, "Other sessions revoked", true);
    }

    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            return new UserDto(user.getId().toString(), user.getEmail(), user.getFirstName(), user.getLastName());
        }
        return null;
    }

   
}
