package com.leaping.portfolio_app.market.security;

import com.leaping.portfolio_app.auth.security.SecurityConfig;
import com.leaping.portfolio_app.auth.service.SessionService;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import com.leaping.portfolio_app.market.controller.MarketOverrideController;
import com.leaping.portfolio_app.market.service.MarketDataIngestionService;
import com.leaping.portfolio_app.market.service.MarketOverride;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketOverrideController.class)
@Import(SecurityConfig.class)
class MarketOverrideSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketOverride marketOverride;

    @MockitoBean
    private MarketDataIngestionService marketDataIngestionService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private SessionService sessionService;

    @Test
    void anonymousCannotReadOrChangeMarketOverride() throws Exception {
        mockMvc.perform(get("/api/market/override"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/market/override")
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                        .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCanReadAndChangeMarketOverride() throws Exception {
        authenticateAs("CUSTOMER");
        when(marketOverride.isAllowed()).thenReturn(true);
        when(marketOverride.isActive()).thenReturn(false);

        mockMvc.perform(get("/api/market/override")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/market/override")
                        .header("Authorization", "Bearer customer-token")
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void nobodyCanTurnOnMarketOverrideWhereItIsNotAllowed() throws Exception {
        authenticateAs("CUSTOMER");
        when(marketOverride.isAllowed()).thenReturn(false);

        mockMvc.perform(put("/api/market/override")
                        .header("Authorization", "Bearer customer-token")
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAndChangeMarketOverride() throws Exception {
        authenticateAs("ADMIN");
        when(marketOverride.isAllowed()).thenReturn(true);
        when(marketOverride.isActive()).thenReturn(false);

        mockMvc.perform(get("/api/market/override")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/market/override")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());
    }

    private void authenticateAs(String role) {
        String token = role.toLowerCase() + "-token";
        String email = role.toLowerCase() + "@example.com";
        when(sessionService.isAccessTokenActive(token)).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn(email);
        UserDetails user = User.withUsername(email)
                .password("unused")
                .roles(role)
                .build();
        when(userDetailsService.loadUserByUsername(email)).thenReturn(user);
    }
}