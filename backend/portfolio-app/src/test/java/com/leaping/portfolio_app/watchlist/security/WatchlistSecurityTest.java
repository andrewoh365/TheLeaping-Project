package com.leaping.portfolio_app.watchlist.security;

import com.leaping.portfolio_app.auth.security.SecurityConfig;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;

import com.leaping.portfolio_app.watchlist.controller.WatchlistController;
import com.leaping.portfolio_app.watchlist.service.WatchlistService;
import com.leaping.portfolio_app.watchlist.dto.CreateWatchlistRequest;
import com.leaping.portfolio_app.watchlist.dto.WatchlistResponse;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WatchlistController.class)
@Import(SecurityConfig.class)
class WatchlistSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WatchlistService watchlistService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    // TEST 1: REQUEST WITHOUT AUTHENTICATION
    @Test
    void getWatchlists_shouldRejectRequestWithoutToken() throws Exception {

        // Act: Send request without Authorization header
        mockMvc.perform(get("/api/watchlists"))
                // Assert: Request is rejected
                .andExpect(status().is4xxClientError());

        // Verify the service was never accessed
        verifyNoInteractions(watchlistService);
    }

    // TEST 2: REQUEST WITH INVALID TOKEN
    @Test
    void getWatchlists_shouldRejectInvalidToken() throws Exception {

        // Arrange: Simulate failed token validation
        when(jwtTokenProvider.validateToken("invalid-token"))
                .thenReturn(false);

        // Act: Send request with invalid token
        mockMvc.perform(get("/api/watchlists")
                .header(
                        "Authorization",
                        "Bearer invalid-token"
                ))
                // Assert: Request is rejected
                .andExpect(status().is4xxClientError());

        // Verify the token was checked
        verify(jwtTokenProvider).validateToken("invalid-token");

        // Verify the service was never accessed
        verifyNoInteractions(watchlistService);
    }

    // TEST 3: VALID CUSTOMER TOKEN (DIAGNOSTIC TEST)
    @Test
    void getWatchlists_shouldAllowValidCustomerToken() throws Exception {

        // Arrange: Simulate a valid JWT
        String token = "valid-token";
        String email = "customer@example.com";

        when(jwtTokenProvider.validateToken(token))
                .thenReturn(true);

        when(jwtTokenProvider.getEmailFromToken(token))
                .thenReturn(email);

        // Create authenticated customer details
        UserDetails customer = User.withUsername(email)
                .password("unused")
                .roles("CUSTOMER")
                .build();

        when(userDetailsService.loadUserByUsername(email))
                .thenReturn(customer);

        when(watchlistService.getWatchlists())
                .thenReturn(List.of());

        // Act: Send the authenticated request
        var result = mockMvc.perform(get("/api/watchlists")
                .header("Authorization", "Bearer " + token));

        // DIAGNOSTIC: Verify each authentication step happened
        // Step 1: Was the token validated?
        verify(jwtTokenProvider).validateToken(token);

        // Step 2: Was the email extracted?
        verify(jwtTokenProvider).getEmailFromToken(token);

        // Step 3: Was the user loaded?
        verify(userDetailsService).loadUserByUsername(email);

        // Assert: Verify the HTTP response
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Verify the request reached the service
        verify(watchlistService).getWatchlists();
    }

    // TEST 4: VALID CUSTOMER CREATES WATCHLIST
    @Test
    void createWatchlist_shouldAllowValidCustomerToken() throws Exception {

        // Arrange: Simulate a valid JWT
        String token = "valid-token";
        String email = "customer@example.com";

        when(jwtTokenProvider.validateToken(token))
                .thenReturn(true);

        when(jwtTokenProvider.getEmailFromToken(token))
                .thenReturn(email);

        UserDetails customer = User.withUsername(email)
                .password("unused")
                .roles("CUSTOMER")
                .build();

        when(userDetailsService.loadUserByUsername(email))
                .thenReturn(customer);

        // Prepare the service response
        OffsetDateTime now = OffsetDateTime.now();

        WatchlistResponse response = new WatchlistResponse(
                10L,
                "Tech Stocks",
                now,
                now
        );

        when(watchlistService.createWatchlist(
                any(CreateWatchlistRequest.class)
        )).thenReturn(response);

        // Act and Assert
        mockMvc.perform(post("/api/watchlists")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                    "watchlistName": "Tech Stocks"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.watchlistId").value(10))
                .andExpect(jsonPath("$.watchlistName")
                        .value("Tech Stocks"));

        // Verify request reached the service
        verify(watchlistService).createWatchlist(
                any(CreateWatchlistRequest.class)
        );
    }

    @Test
    void getWatchlists_shouldRejectTokenWhenUserNoLongerExists() throws Exception {

        String token = "valid-token";
        String email = "deleted@example.com";

        when(jwtTokenProvider.validateToken(token))
                .thenReturn(true);

        when(jwtTokenProvider.getEmailFromToken(token))
                .thenReturn(email);

        when(userDetailsService.loadUserByUsername(email))
                .thenThrow(new UsernameNotFoundException("User not found"));

        mockMvc.perform(get("/api/watchlists")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        verify(jwtTokenProvider).validateToken(token);
        verify(userDetailsService).loadUserByUsername(email);
        verifyNoInteractions(watchlistService);
    }

    @Test
    void getWatchlists_shouldRejectTokenWithoutBearerPrefix() throws Exception {

        mockMvc.perform(get("/api/watchlists")
                .header("Authorization", "valid-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(watchlistService);
    }
}
