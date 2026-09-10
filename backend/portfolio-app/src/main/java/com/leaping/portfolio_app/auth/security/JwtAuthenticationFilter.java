package com.leaping.portfolio_app.auth.security;

import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter - Validates JWT tokens on every request
 * 
 * This filter:
 * 1. Extracts JWT token from Authorization header
 * 2. Validates the token signature and expiration
 * 3. Extracts email from token
 * 4. Loads user details (including role) from database
 * 5. Sets Spring Security context with user info
 * 6. Allows request to proceed with user authenticated
 * 
 * Flow:
 * Request → Extract token → Validate → Load user → Set SecurityContext → Proceed
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Called on every HTTP request
     * Checks for JWT token and sets up Spring Security context if valid
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        try {
            // STEP 1: Extract token from Authorization header
            // Expected format: "Authorization: Bearer <token>"
            String authHeader = request.getHeader("Authorization");
            String token = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);  // Remove "Bearer " prefix
            }

            // STEP 2: If we have a token, validate it
            if (token != null && jwtTokenProvider.validateToken(token)) {
                
                // STEP 3: Extract email from token
                String email = jwtTokenProvider.getEmailFromToken(token);

                // STEP 4: Load user details from database (includes role)
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // STEP 5: Create authentication token with user details and roles
                // This includes the user's roles from UserDetails
                UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(
                        userDetails,                    // Principal (the user)
                        null,                           // Credentials (not needed, already authenticated)
                        userDetails.getAuthorities()    // Authorities (roles like ROLE_ADMIN)
                    );

                // STEP 6: Set the authentication in Spring Security context
                // This makes the user "logged in" for this request
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        } catch (Exception e) {
            // If anything goes wrong (invalid token, user not found, etc.)
            // Log it but continue - endpoint @PreAuthorize will handle unauthorized requests
            logger.error("Cannot set user authentication: " + e.getMessage(), e);
        }

        // STEP 7: Continue the filter chain
        // The next filter/handler will see the authenticated user in SecurityContext
        filterChain.doFilter(request, response);
    }
}
