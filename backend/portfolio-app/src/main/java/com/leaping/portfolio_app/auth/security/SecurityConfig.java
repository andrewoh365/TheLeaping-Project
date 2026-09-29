package com.leaping.portfolio_app.auth.security;

import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enable @PreAuthorize annotations on methods
public class SecurityConfig {

    @Value("${server.cors.allowed-origins:http://localhost:4200,http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Create JWT authentication filter
     * This filter validates JWT tokens on every request
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                                           UserDetailsService userDetailsService) {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String[] origins = allowedOrigins.trim().split(",\\s*");
        
        // Remove wildcard if present
        java.util.List<String> originList = new java.util.ArrayList<>();
        for (String origin : origins) {
            String trimmedOrigin = origin.trim();
            if (!"*".equals(trimmedOrigin)) {
                originList.add(trimmedOrigin);
            }
        }
        
        String[] finalOrigins = originList.isEmpty() 
            ? new String[]{"http://localhost:4200", "http://localhost:3000", "http://localhost:58261"}
            : originList.toArray(new String[0]);
        
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.Arrays.asList(finalOrigins));
        configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(java.util.Arrays.asList("Content-Type", "Authorization", "X-Requested-With"));
        configuration.setExposedHeaders(java.util.Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/api/auth/login").permitAll()        // Login endpoint
                        .requestMatchers("/api/auth/register").permitAll()     // Register endpoint
                        .requestMatchers("/api/auth/validate").permitAll()     // Token validation
                        .requestMatchers("/api/auth/refresh").permitAll()      // Token refresh
                        .requestMatchers("/api/auth/health").permitAll()       // Health check
                        .requestMatchers("/api/auth/instrument/**").permitAll() // Instrument endpoints - temporarily allowed
                        .requestMatchers("/api/public/**").permitAll()         // Public endpoints
                        .requestMatchers("/auth/**").permitAll()               // Auth endpoints without /api prefix
                        .anyRequest().authenticated()                          // Everything else requires authentication
                )
                // Add JWT filter BEFORE the standard username/password filter
                // This way, JWT tokens are validated first
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable());

        return http.build();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Parse origins from config, filter out wildcards when credentials are enabled
                String[] origins = allowedOrigins.trim().split(",\\s*");
                
                // Remove wildcard if present (incompatible with allowCredentials=true)
                java.util.List<String> originList = new java.util.ArrayList<>();
                for (String origin : origins) {
                    String trimmedOrigin = origin.trim();
                    if (!"*".equals(trimmedOrigin)) {
                        originList.add(trimmedOrigin);
                    }
                }
                
                String[] finalOrigins = originList.isEmpty() 
                    ? new String[]{"http://localhost:4200", "http://localhost:3000", "http://localhost:58261"}
                    : originList.toArray(new String[0]);
                
                registry.addMapping("/**")
                        .allowedOrigins(finalOrigins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders("Content-Type", "Authorization", "X-Requested-With")
                        .exposedHeaders("Authorization")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
