package com.leaping.portfolio_app.auth.security;

import com.leaping.portfolio_app.auth.service.SessionService;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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
                                                           UserDetailsService userDetailsService,
                                                           SessionService sessionService) {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService, sessionService);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);

        registration.setEnabled(false);

        return registration;
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
                        .requestMatchers( "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/auth/health").permitAll()      // All auth endpoints are public
                        .requestMatchers("/api/public/**").permitAll()    // Public endpoints
                        .requestMatchers("/api/market/news", "/api/market/movers").permitAll() // Public dashboard insights
                        .requestMatchers("/auth/**").permitAll()          // Auth endpoints without /api prefix
                        .requestMatchers("/api/market/instruments/price/history").permitAll()
                        // Error dispatches carry no JWT; without this, a 404 or 503
                        // from a controller is rewritten to 401 and looks like an
                        // expired session to the frontend.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()                     // Everything else requires authentication
                )
                // Add JWT filter BEFORE the standard username/password filter
                // This way, JWT tokens are validated first
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Missing, invalid or expired tokens get 401 so the frontend
                // knows to refresh; 403 is left for authenticated users who
                // lack the required role.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                        )
                )
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
