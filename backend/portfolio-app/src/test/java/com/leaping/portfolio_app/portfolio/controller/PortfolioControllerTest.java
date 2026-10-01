package com.leaping.portfolio_app.portfolio.controller;

import com.leaping.portfolio_app.auth.service.AuthService;
import com.leaping.portfolio_app.portfolio.dto.PortfolioResponse;
import com.leaping.portfolio_app.portfolio.service.PortfolioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PortfolioController.
 *
 * This controller reads the logged-in user out of Spring Security's
 * SecurityContextHolder - normally JwtAuthenticationFilter puts that there
 * before the controller ever runs. Here, there's no real filter and no real
 * JWT, so setUpSecurityContext() fakes that same end state by hand.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    private static final String TEST_EMAIL = "testuser2@example.com";

    @Mock
    private AuthService authService;

    @Mock
    private PortfolioService portfolioService;

    @InjectMocks
    private PortfolioController portfolioController;

    @BeforeEach
    void setUpSecurityContext() {
        // A plain String principal is enough here - Authentication.getName()
        // returns principal.toString() when the principal isn't a UserDetails,
        // which for a String is just the string itself (the email).
        Authentication authentication = new UsernamePasswordAuthenticationToken(TEST_EMAIL, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        // SecurityContextHolder is backed by a ThreadLocal - clear it after
        // each test so one test's fake login can't leak into another test.
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyPortfolio_resolvesEmailToIdThenReturnsPortfolio() {
        Long customerId = 5L;
        PortfolioResponse expectedResponse = new PortfolioResponse(
                customerId, new BigDecimal("0.00"), Collections.emptyList());

        when(authService.getUserIdByEmail(TEST_EMAIL)).thenReturn(customerId);
        when(portfolioService.getPortfolioForCustomer(customerId)).thenReturn(expectedResponse);

        ResponseEntity<PortfolioResponse> result = portfolioController.getMyPortfolio();

        assertEquals(200, result.getStatusCode().value());
        // assertSame checks it's literally the same object reference the mock
        // returned - appropriate here since PortfolioResponse has no equals().
        assertSame(expectedResponse, result.getBody());

        // Confirms the controller actually called both collaborators,
        // in the email -> id -> portfolio chain we built this around.
        verify(authService).getUserIdByEmail(TEST_EMAIL);
        verify(portfolioService).getPortfolioForCustomer(customerId);
    }
}
