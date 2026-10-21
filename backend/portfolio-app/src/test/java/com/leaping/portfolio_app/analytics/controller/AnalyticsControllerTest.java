package com.leaping.portfolio_app.analytics.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.leaping.portfolio_app.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AnalyticsController(analyticsService)
                )
                .build();
    }

    @Test
    void getDashboard_forwardsValidClientSegment() throws Exception {
        LocalDate dateFrom = LocalDate.of(2026, 9, 1);
        LocalDate dateTo = LocalDate.of(2026, 9, 30);

        when(analyticsService.getDashboard(
                dateFrom,
                dateTo,
                "STOCK",
                "NASDAQ",
                "FILLED",
                "FREQUENT"
        )).thenReturn("{}");

        mockMvc.perform(get("/api/analytics/dashboard")
                        .param("date_from", "2026-09-01")
                        .param("date_to", "2026-09-30")
                        .param("asset_type", "STOCK")
                        .param("market", "NASDAQ")
                        .param("order_status", "FILLED")
                        .param("client_segment", "FREQUENT"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{}"));

        verify(analyticsService).getDashboard(
                dateFrom,
                dateTo,
                "STOCK",
                "NASDAQ",
                "FILLED",
                "FREQUENT"
        );
    }

    @Test
    void getDashboard_omitsClientSegmentWhenNotProvided() throws Exception {
        when(analyticsService.getDashboard(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        )).thenReturn("{}");

        mockMvc.perform(get("/api/analytics/dashboard"))
                .andExpect(status().isOk());

        verify(analyticsService).getDashboard(
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    void getDashboard_rejectsInvalidClientSegment() throws Exception {
        mockMvc.perform(get("/api/analytics/dashboard")
                        .param("client_segment", "VERY_ACTIVE"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(analyticsService);
    }
}
