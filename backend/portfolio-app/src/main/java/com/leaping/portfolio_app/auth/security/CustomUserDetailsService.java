package com.leaping.portfolio_app.auth.security;

import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * CustomUserDetailsService implements Spring Security's UserDetailsService
 * 
 * Purpose: Load user details from our UserRepository for Spring Security
 * This bridges our User model with Spring Security's requirements
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Load user by email (Spring Security calls this during authentication)
     * 
     * @param email the username (in our case, email)
     * @return UserDetails with credentials and authorities (roles)
     * @throws UsernameNotFoundException if user not found
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Find user by email in our repository
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // Convert our User to Spring's UserDetails
        // This includes: username, password, and authorities (roles)
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())                    // Username = email
                .password(user.getPassword())                 // Password (already hashed)
                .authorities(
                    // Convert UserRole to Spring GrantedAuthority
                    // Spring expects "ROLE_" prefix, so ADMIN becomes "ROLE_ADMIN"
                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                )
                .build();
    }
}
