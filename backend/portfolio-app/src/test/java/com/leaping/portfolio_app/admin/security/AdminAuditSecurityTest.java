package com.leaping.portfolio_app.admin.security;

import com.leaping.portfolio_app.admin.controller.AdminClientController;
import com.leaping.portfolio_app.admin.controller.AdminTradeLookupController;
import com.leaping.portfolio_app.admin.service.AdminClientService;
import com.leaping.portfolio_app.admin.service.AdminTradeLookupService;
import com.leaping.portfolio_app.auth.security.SecurityConfig;
import com.leaping.portfolio_app.auth.service.SessionService;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminClientController.class, AdminTradeLookupController.class})
@Import(SecurityConfig.class)
class AdminAuditSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminClientService adminClientService;

    @MockitoBean
    private AdminTradeLookupService adminTradeLookupService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

        @MockitoBean
        private SessionService sessionService;

    @Test
    void adminCanAccessBothAuditEndpoints() throws Exception {
        authenticateAs("ADMIN");
        when(adminClientService.getClientActivity(anyLong(), anyInt(), anyInt()))
                .thenReturn(Page.empty());
        when(adminTradeLookupService.getOrderAudit(anyLong(), anyInt(), anyInt()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/clients/7/activity")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/orders/42/audit")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotAccessAuditEndpoints() throws Exception {
        authenticateAs("CUSTOMER");

        mockMvc.perform(get("/api/admin/clients/7/activity")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders/42/audit")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminClientService, adminTradeLookupService);
    }

    @Test
    void analystCannotAccessAuditEndpoints() throws Exception {
        authenticateAs("ANALYST");

        mockMvc.perform(get("/api/admin/clients/7/activity")
                        .header("Authorization", "Bearer analyst-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders/42/audit")
                        .header("Authorization", "Bearer analyst-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminClientService, adminTradeLookupService);
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