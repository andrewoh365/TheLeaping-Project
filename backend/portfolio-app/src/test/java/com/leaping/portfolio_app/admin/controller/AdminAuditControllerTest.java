package com.leaping.portfolio_app.admin.controller;

import com.leaping.portfolio_app.admin.dto.AuditEventResponse;
import com.leaping.portfolio_app.admin.service.AdminClientService;
import com.leaping.portfolio_app.admin.service.AdminTradeLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAuditControllerTest {

    @Mock
    private AdminClientService adminClientService;

    @Mock
    private AdminTradeLookupService adminTradeLookupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminClientController(adminClientService, adminTradeLookupService),
                new AdminTradeLookupController(adminTradeLookupService)
        ).build();
    }

    @Test
    void getClientActivityReturnsPagedAuditDto() throws Exception {
        AuditEventResponse response = event(7L, "CASH_BALANCE_CHANGED");
        when(adminClientService.getClientActivity(7L, 2, 10))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(2, 10), 1));

        mockMvc.perform(get("/api/admin/clients/7/activity")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].auditEventId").value(7))
                .andExpect(jsonPath("$.content[0].eventType").value("CASH_BALANCE_CHANGED"))
                .andExpect(jsonPath("$.content[0].eventDetails.amountUsd").value(-25.0));

        verify(adminClientService).getClientActivity(7L, 2, 10);
    }

    @Test
    void getOrderAuditReturnsChronologicalDtoPage() throws Exception {
        when(adminTradeLookupService.getOrderAudit(42L, 0, 100))
                .thenReturn(new PageImpl<>(List.of(
                        event(1L, "ORDER_SUBMITTED"),
                        event(2L, "TRADE_FILLED")
                ), PageRequest.of(0, 100), 2));

        mockMvc.perform(get("/api/admin/orders/42/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("ORDER_SUBMITTED"))
                .andExpect(jsonPath("$.content[1].eventType").value("TRADE_FILLED"));

        verify(adminTradeLookupService).getOrderAudit(42L, 0, 100);
    }

    @Test
    void missingClientActivityUsesNotFoundResponse() throws Exception {
        when(adminClientService.getClientActivity(404L, 0, 25))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

        mockMvc.perform(get("/api/admin/clients/404/activity"))
                .andExpect(status().isNotFound());
    }

    private AuditEventResponse event(Long id, String type) {
        return new AuditEventResponse(
                id,
                type,
                OffsetDateTime.parse("2026-10-14T10:00:00Z"),
                7L,
                type.equals("CASH_BALANCE_CHANGED") ? null : 42L,
                null,
                null,
                type.equals("CASH_BALANCE_CHANGED") ? 91L : null,
                type.equals("CASH_BALANCE_CHANGED")
                        ? Map.of("amountUsd", new java.math.BigDecimal("-25.00"))
                        : Map.of()
        );
    }
}