package com.leaping.portfolio_app.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.service.MarketPriceService;
import com.leaping.portfolio_app.portfolio.entity.Holding;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.HoldingRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import com.leaping.portfolio_app.trade.dto.PortfolioOverviewResponse;
import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioOverviewServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private MarketPriceService marketPriceService;

    @InjectMocks
    private PortfolioOverviewService portfolioOverviewService;

    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setUserId(7L);

        portfolio = new Portfolio();
        portfolio.setPortfolioId(15L);
        portfolio.setCustomer(customer);
        portfolio.setCashBalanceUsd(new BigDecimal("1000.00"));
        portfolio.setUpdatedAt(OffsetDateTime.parse("2026-09-30T12:00:00Z"));
    }

    @Test
    void getPortfolioOverviewShouldReturnSummaryAndHoldingsUsingCurrentPrices() {
        Instrument aapl = new Instrument();
        aapl.setInstrumentId(1L);
        aapl.setSymbol("AAPL");
        aapl.setName("Apple Inc.");

        Holding aaplHolding = new Holding();
        aaplHolding.setHoldingId(21L);
        aaplHolding.setPortfolio(portfolio);
        aaplHolding.setInstrument(aapl);
        aaplHolding.setQuantity(new BigDecimal("2"));
        aaplHolding.setAverageCostUsd(new BigDecimal("200.00"));

        Instrument msft = new Instrument();
        msft.setInstrumentId(2L);
        msft.setSymbol("MSFT");
        msft.setName("Microsoft Corporation");

        Holding msftHolding = new Holding();
        msftHolding.setHoldingId(22L);
        msftHolding.setPortfolio(portfolio);
        msftHolding.setInstrument(msft);
        msftHolding.setQuantity(new BigDecimal("1"));
        msftHolding.setAverageCostUsd(new BigDecimal("450.00"));

        Trade latestTrade = new Trade();
        latestTrade.setExecutedAt(OffsetDateTime.parse("2026-09-30T15:30:00Z"));

        when(portfolioRepository.findByCustomerUserId(7L)).thenReturn(Optional.of(portfolio));
        when(holdingRepository.findByPortfolioAndQuantityGreaterThan(portfolio, BigDecimal.ZERO))
            .thenReturn(List.of(msftHolding, aaplHolding));
        when(marketPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("222.50"));
        when(marketPriceService.getCurrentPrice("MSFT")).thenReturn(new BigDecimal("431.25"));
        when(tradeRepository.findTopByOrderPortfolioOrderByExecutedAtDesc(portfolio))
            .thenReturn(Optional.of(latestTrade));

        PortfolioOverviewResponse response = portfolioOverviewService.getPortfolioOverview(7L);

        assertNotNull(response);
        assertEquals(OffsetDateTime.parse("2026-09-30T15:30:00Z"), response.getLastExecutedTradeAt());
        assertEquals(2, response.getHoldings().size());
        assertEquals("AAPL", response.getHoldings().get(0).getSymbol());
        assertEquals(new BigDecimal("445.00"), response.getHoldings().get(0).getCurrentValueUsd());
        assertEquals(new BigDecimal("45.00"), response.getHoldings().get(0).getGainLossUsd());
        assertEquals(new BigDecimal("-18.75"), response.getHoldings().get(1).getGainLossUsd());
        assertEquals(new BigDecimal("26.25"), response.getSummary().getTotalGainLossUsd());
        assertEquals(new BigDecimal("876.25"), response.getSummary().getTotalHoldingsValueUsd());
        assertEquals(new BigDecimal("1876.25"), response.getSummary().getTotalPortfolioValueUsd());
        assertEquals(new BigDecimal("1000.00"), response.getSummary().getBuyingPowerUsd());
        assertEquals(new BigDecimal("3.09"), response.getSummary().getTotalGainLossPercent());
    }

    @Test
    void getPortfolioOverviewShouldFallbackToPortfolioUpdatedAtWhenNoTradesExist() {
        when(portfolioRepository.findByCustomerUserId(7L)).thenReturn(Optional.of(portfolio));
        when(holdingRepository.findByPortfolioAndQuantityGreaterThan(portfolio, BigDecimal.ZERO))
            .thenReturn(List.of());
        when(tradeRepository.findTopByOrderPortfolioOrderByExecutedAtDesc(portfolio))
            .thenReturn(Optional.empty());

        PortfolioOverviewResponse response = portfolioOverviewService.getPortfolioOverview(7L);

        assertEquals(portfolio.getUpdatedAt(), response.getLastExecutedTradeAt());
        assertEquals(new BigDecimal("0.00"), response.getSummary().getTotalHoldingsValueUsd());
        assertEquals(new BigDecimal("1000.00"), response.getSummary().getCashBalanceUsd());
    }

    @Test
    void getPortfolioOverviewShouldFailWhenPortfolioMissing() {
        when(portfolioRepository.findByCustomerUserId(7L)).thenReturn(Optional.empty());

        assertThrows(
            IllegalArgumentException.class,
            () -> portfolioOverviewService.getPortfolioOverview(7L)
        );
    }
}