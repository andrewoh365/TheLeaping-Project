package com.leaping.portfolio_app.portfolio.service;

import com.leaping.portfolio_app.market.service.MarketPriceService;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.holdings.HoldingResponse;
import com.leaping.portfolio_app.portfolio.dto.PortfolioResponse;
import com.leaping.portfolio_app.holdings.Holding;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.holdings.HoldingRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PortfolioService.
 *
 * These are "sociable" with nothing real - PortfolioRepository and
 * HoldingRepository are both faked with @Mock, so no database is touched
 * and these tests run instantly.
 *
 * @ExtendWith(MockitoExtension.class) tells JUnit to let Mockito process
 * the @Mock/@InjectMocks annotations below before each test runs.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private HoldingRepository holdingRepository;
    
    @Mock
    private MarketPriceService marketPriceService;

    // Mockito builds a real PortfolioService, but hands it the two @Mock
    // fields above instead of real repositories - same constructor
    // injection Spring would normally do, just done by hand for the test.
    @InjectMocks
    private PortfolioService portfolioService;

    @Test
    void getPortfolioForCustomer_returnsPortfolioWithNoHoldings() {
        Long customerId = 5L;

        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(10L);
        portfolio.setCashBalanceUsd(new BigDecimal("0.00"));

        // Tell the fake repositories what to hand back when PortfolioService calls them
        when(portfolioRepository.findByCustomer_UserId(customerId))
                .thenReturn(Optional.of(portfolio));
        when(holdingRepository.findAllByPortfolio_PortfolioIdOrderByUpdatedAtDesc(10L))
                .thenReturn(List.of());

        PortfolioResponse response = portfolioService.getPortfolioForCustomer(customerId);

        assertEquals(10L, response.getPortfolioId());
        assertEquals(new BigDecimal("0.00"), response.getCashBalanceUsd());
        assertTrue(response.getHoldings().isEmpty());
    }

    @Test
    void getPortfolioForCustomer_mapsHoldingsToHoldingResponseCorrectly() {
        Long customerId = 5L;

        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(10L);
        portfolio.setCashBalanceUsd(new BigDecimal("1250.00"));

        // market/instrumentType/priceCurrency are irrelevant to this test,
        // so they're left null - only symbol/name matter here.
        Instrument instrument = new Instrument(null, "AAPL", "Apple Inc.", null, null);
        Holding holding = new Holding(portfolio, instrument, new BigDecimal("10.5"), new BigDecimal("172.30"));

        when(portfolioRepository.findByCustomer_UserId(customerId))
                .thenReturn(Optional.of(portfolio));
        when(holdingRepository.findAllByPortfolio_PortfolioIdOrderByUpdatedAtDesc(10L))
                .thenReturn(List.of(holding));

        PortfolioResponse response = portfolioService.getPortfolioForCustomer(customerId);

        assertEquals(1, response.getHoldings().size());

        HoldingResponse holdingResponse = response.getHoldings().get(0);
        assertEquals("AAPL", holdingResponse.getSymbol());
        assertEquals("Apple Inc.", holdingResponse.getInstrumentName());
        assertEquals(new BigDecimal("10.5"), holdingResponse.getQuantity());
        assertEquals(new BigDecimal("172.30"), holdingResponse.getAverageCostUsd());
    }

    @Test
    void getPortfolioForCustomer_throwsWhenNoPortfolioExists() {
        Long customerId = 999L;

        when(portfolioRepository.findByCustomer_UserId(customerId))
                .thenReturn(Optional.empty());

        // .orElseThrow(...) inside the service should surface as a RuntimeException here
        assertThrows(RuntimeException.class,
                () -> portfolioService.getPortfolioForCustomer(customerId));
    }
}
