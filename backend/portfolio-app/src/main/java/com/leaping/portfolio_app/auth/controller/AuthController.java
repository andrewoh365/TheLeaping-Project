package com.leaping.portfolio_app.auth.controller;

import com.leaping.portfolio_app.auth.dto.AuthResponse;
import com.leaping.portfolio_app.auth.dto.LoginRequest;
import com.leaping.portfolio_app.auth.dto.RegisterRequest;
import com.leaping.portfolio_app.auth.dto.RegisterResponse;
import com.leaping.portfolio_app.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest loginRequest) {
        AuthResponse response = authService.authenticate(loginRequest);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * Register endpoint - creates a new user account
     * 
     * Request body should contain:
     * {
     *   "email": "user@example.com",
     *   "password": "SecurePassword123!",
     *   "confirmPassword": "SecurePassword123!",
     *   "firstName": "John",
     *   "lastName": "Doe"
     * }
     * 
     * Returns 201 CREATED if successful, 400 BAD REQUEST if validation fails
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest registerRequest) {
        // Call the register logic in AuthService
        RegisterResponse response = authService.register(registerRequest);
        
        // Return 201 CREATED if registration successful, 400 BAD REQUEST if failed
        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout() {
        AuthResponse response = authService.logout();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate")
    public ResponseEntity<AuthResponse> validateToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, "Missing or invalid authorization header", false));
        }

        String token = authHeader.substring(7);
        boolean isValid = authService.validateToken(token);

        if (isValid) {
            String email = authService.getEmailFromToken(token);
            return ResponseEntity.ok(new AuthResponse(token, email, "Token is valid", true));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, "Invalid or expired token", false));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth service is running");
    }
}
