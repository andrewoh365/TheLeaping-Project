package com.leaping.portfolio_app.analytics.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leaping.portfolio_app.PortfolioAppApplication;
import com.leaping.portfolio_app.auth.service.SessionService;
import com.leaping.portfolio_app.analytics.service.AnalyticsService;
import com.leaping.portfolio_app.auth.security.SecurityConfig;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsController.class)
@Import(SecurityConfig.class)
@ContextConfiguration(classes = PortfolioAppApplication.class)
class AnalyticsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private SessionService sessionService;

    @Test
    void analystCanAccessDashboardWithClientSegment() throws Exception {
        String token = "analyst-token";
        String email = "analyst@example.com";

        when(sessionService.isAccessTokenActive(token)).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(
            User.withUsername(email)
                .password("unused")
                .roles("ANALYST")
                .build()
        );

        when(analyticsService.getDashboard(
                isNull(LocalDate.class),
                isNull(LocalDate.class),
                isNull(String.class),
                isNull(String.class),
                isNull(Long.class),
                isNull(String.class),
                eq("FREQUENT")
        )).thenReturn("{}");

            mockMvc.perform(get("/api/analytics/dashboard")
                    .header("Authorization", "Bearer " + token)
                    .param("client_segment", "FREQUENT"))
                .andExpect(status().isOk());
    }

            @Test
            void adminCanAccessDashboardWithClientSegment() throws Exception {
            String token = "admin-token";
            String email = "admin@example.com";

            when(sessionService.isAccessTokenActive(token)).thenReturn(true);
            when(jwtTokenProvider.getEmailFromToken(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(
                User.withUsername(email)
                    .password("unused")
                    .roles("ADMIN")
                    .build()
            );

            when(analyticsService.getDashboard(
                isNull(LocalDate.class),
                isNull(LocalDate.class),
                isNull(String.class),
                isNull(String.class),
                isNull(Long.class),
                isNull(String.class),
                eq("FREQUENT")
            )).thenReturn("{}");

            mockMvc.perform(get("/api/analytics/dashboard")
                    .header("Authorization", "Bearer " + token)
                    .param("client_segment", "FREQUENT"))
                .andExpect(status().isOk());
            }

    @Test
    void customerCannotAccessDashboard() throws Exception {
            String token = "customer-token";
            String email = "customer@example.com";

            when(sessionService.isAccessTokenActive(token)).thenReturn(true);
            when(jwtTokenProvider.getEmailFromToken(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(
                User.withUsername(email)
                    .password("unused")
                    .roles("CUSTOMER")
                    .build()
            );

        mockMvc.perform(get("/api/analytics/dashboard")
                    .header("Authorization", "Bearer " + token)
                        .param("client_segment", "FREQUENT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(analyticsService);
    }
}
