package com.leaping.portfolio_app.portfolio.service;

import com.leaping.portfolio_app.holdings.Holding;
import com.leaping.portfolio_app.holdings.HoldingRepository;
import com.leaping.portfolio_app.holdings.HoldingResponse;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.service.MarketPriceService;
import com.leaping.portfolio_app.portfolio.dto.PortfolioResponse;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private MarketPriceService marketPriceService;

    @InjectMocks
    private PortfolioService portfolioService;

    @Test
    void getPortfolioForCustomer_returnsPortfolioWithNoHoldings() {

        Long customerId = 5L;

        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(10L);
        portfolio.setCashBalanceUsd(
            new BigDecimal("0.00")
        );

        when(
            portfolioRepository
                .findByCustomer_UserId(customerId)
        ).thenReturn(
            Optional.of(portfolio)
        );

        when(
            holdingRepository
                .findAllByPortfolio_PortfolioIdOrderByUpdatedAtDesc(
                    10L
                )
        ).thenReturn(
            List.of()
        );

        PortfolioResponse response =
            portfolioService
                .getPortfolioForCustomer(customerId);

        assertEquals(
            10L,
            response.getPortfolioId()
        );

        assertEquals(
            new BigDecimal("0.00"),
            response.getCashBalanceUsd()
        );

        assertTrue(
            response.getHoldings().isEmpty()
        );
    }

    @Test
    void getPortfolioForCustomer_mapsHoldingsToHoldingResponseCorrectly() {

        Long customerId = 5L;

        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(10L);
        portfolio.setCashBalanceUsd(
            new BigDecimal("1250.00")
        );

        Instrument instrument =
            new Instrument(
                null,
                "AAPL",
                "Apple Inc.",
                null,
                null
            );

        instrument.setInstrumentId(1L);

        Holding holding =
            new Holding(
                portfolio,
                instrument,
                new BigDecimal("10.5"),
                new BigDecimal("172.30")
            );

        BigDecimal currentPrice =
            new BigDecimal("180.00");

        when(
            portfolioRepository
                .findByCustomer_UserId(customerId)
        ).thenReturn(
            Optional.of(portfolio)
        );

        when(
            holdingRepository
                .findAllByPortfolio_PortfolioIdOrderByUpdatedAtDesc(
                    10L
                )
        ).thenReturn(
            List.of(holding)
        );

        when(
            marketPriceService
                .getCurrentPrice("AAPL")
        ).thenReturn(
            currentPrice
        );

        PortfolioResponse response =
            portfolioService
                .getPortfolioForCustomer(customerId);

        assertEquals(
            1,
            response.getHoldings().size()
        );

        HoldingResponse holdingResponse =
            response.getHoldings().get(0);

        assertEquals(
            "AAPL",
            holdingResponse.getSymbol()
        );

        assertEquals(
            "Apple Inc.",
            holdingResponse.getInstrumentName()
        );

        assertEquals(
            new BigDecimal("10.5"),
            holdingResponse.getQuantity()
        );

        assertEquals(
            new BigDecimal("172.30"),
            holdingResponse.getAverageCostUsd()
        );

        assertEquals(
            currentPrice,
            holdingResponse.getCurrentPrice()
        );

        assertEquals(
            new BigDecimal("1890.000"),
            holdingResponse.getCurrentValueUsd()
        );

        verify(
            marketPriceService
        ).getCurrentPrice("AAPL");
    }

    @Test
    void getPortfolioForCustomer_throwsWhenNoPortfolioExists() {

        Long customerId = 999L;

        when(
            portfolioRepository
                .findByCustomer_UserId(customerId)
        ).thenReturn(
            Optional.empty()
        );

        assertThrows(
            RuntimeException.class,
            () ->
                portfolioService
                    .getPortfolioForCustomer(customerId)
        );
    }
}