package com.leaping.portfolio_app.portfolio.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import com.leaping.portfolio_app.portfolio.service.PortfolioOverviewService;
import com.leaping.portfolio_app.trade.dto.HoldingDto;
import com.leaping.portfolio_app.trade.dto.PortfolioOverviewResponse;
import com.leaping.portfolio_app.trade.dto.PortfolioSummaryDto;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PortfolioOverviewService portfolioOverviewService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PortfolioController portfolioController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(portfolioController).build();
    }

    @Test
    void getOverviewShouldReturnOkResponseWhenPortfolioExists() throws Exception {
        User authenticatedUser = new User();
        authenticatedUser.setId(7L);
        authenticatedUser.setEmail("john.trader@example.com");

        when(userRepository.findByEmail("john.trader@example.com")).thenReturn(Optional.of(authenticatedUser));
        when(portfolioOverviewService.getPortfolioOverview(7L)).thenReturn(buildOverviewResponse());

        mockMvc.perform(get("/api/portfolio/overview").principal(authenticationFor("john.trader@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.portfolio_id").value(15))
            .andExpect(jsonPath("$.summary.cash_balance_usd").value(1000.00))
            .andExpect(jsonPath("$.summary.total_holdings_value_usd").value(445.00))
            .andExpect(jsonPath("$.summary.total_portfolio_value_usd").value(1445.00))
            .andExpect(jsonPath("$.summary.total_gain_loss_usd").value(45.00))
            .andExpect(jsonPath("$.summary.total_gain_loss_percent").value(11.25))
            .andExpect(jsonPath("$.summary.buying_power_usd").value(1000.00))
            .andExpect(jsonPath("$.holdings[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$.holdings[0].current_value_usd").value(445.00))
            .andExpect(jsonPath("$.last_executed_trade_at").value("2026-09-30T15:30:00Z"));
    }

    @Test
    void getOverviewShouldReturnNotFoundWhenPortfolioMissing() throws Exception {
        User authenticatedUser = new User();
        authenticatedUser.setId(7L);
        authenticatedUser.setEmail("john.trader@example.com");

        when(userRepository.findByEmail("john.trader@example.com")).thenReturn(Optional.of(authenticatedUser));
        when(portfolioOverviewService.getPortfolioOverview(7L))
            .thenThrow(new IllegalArgumentException("Portfolio not found for user"));

        mockMvc.perform(get("/api/portfolio/overview").principal(authenticationFor("john.trader@example.com")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Portfolio not found for user"));
    }

    @Test
    void getOverviewShouldReturnNotFoundWhenAuthenticatedUserIsMissing() throws Exception {
        when(userRepository.findByEmail("john.trader@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/portfolio/overview").principal(authenticationFor("john.trader@example.com")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Authenticated user not found"));
    }

    @Test
    void getOverviewShouldReturnInternalServerErrorWhenUnexpectedFailureOccurs() throws Exception {
        User authenticatedUser = new User();
        authenticatedUser.setId(7L);
        authenticatedUser.setEmail("john.trader@example.com");

        when(userRepository.findByEmail("john.trader@example.com")).thenReturn(Optional.of(authenticatedUser));
        when(portfolioOverviewService.getPortfolioOverview(anyLong())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/portfolio/overview").principal(authenticationFor("john.trader@example.com")))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Failed to load portfolio overview: boom"));
    }

    private PortfolioOverviewResponse buildOverviewResponse() {
        PortfolioSummaryDto summary = new PortfolioSummaryDto();
        summary.setPortfolioId(15L);
        summary.setCashBalanceUsd(new BigDecimal("1000.00"));
        summary.setBuyingPowerUsd(new BigDecimal("1000.00"));
        summary.setTotalHoldingsValueUsd(new BigDecimal("445.00"));
        summary.setTotalPortfolioValueUsd(new BigDecimal("1445.00"));
        summary.setTotalGainLossUsd(new BigDecimal("45.00"));
        summary.setTotalGainLossPercent(new BigDecimal("11.25"));

        HoldingDto holding = new HoldingDto();
        holding.setHoldingId(21L);
        holding.setInstrumentId(1L);
        holding.setSymbol("AAPL");
        holding.setInstrumentName("Apple Inc.");
        holding.setQuantity(new BigDecimal("2"));
        holding.setAverageCostUsd(new BigDecimal("200.00"));
        holding.setCurrentPrice(new BigDecimal("222.50"));
        holding.setCurrentValueUsd(new BigDecimal("445.00"));
        holding.setGainLossUsd(new BigDecimal("45.00"));
        holding.setGainLossPercent(new BigDecimal("11.25"));

        PortfolioOverviewResponse response = new PortfolioOverviewResponse();
        response.setSummary(summary);
        response.setHoldings(List.of(holding));
        response.setLastExecutedTradeAt(OffsetDateTime.parse("2026-09-30T15:30:00Z"));
        return response;
    }

    private Authentication authenticationFor(String email) {
        return new UsernamePasswordAuthenticationToken(email, "N/A", List.of());
    }
}
