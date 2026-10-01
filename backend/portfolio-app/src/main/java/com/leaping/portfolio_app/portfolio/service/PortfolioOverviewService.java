package com.leaping.portfolio_app.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leaping.portfolio_app.market.service.MarketPriceService;
import com.leaping.portfolio_app.portfolio.entity.Holding;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.HoldingRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import com.leaping.portfolio_app.trade.dto.HoldingDto;
import com.leaping.portfolio_app.trade.dto.PortfolioOverviewResponse;
import com.leaping.portfolio_app.trade.dto.PortfolioSummaryDto;
import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

@Service
@Transactional(readOnly = true)
public class PortfolioOverviewService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final MarketPriceService marketPriceService;

    public PortfolioOverviewService(
        PortfolioRepository portfolioRepository,
        HoldingRepository holdingRepository,
        TradeRepository tradeRepository,
        MarketPriceService marketPriceService
    ) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
        this.marketPriceService = marketPriceService;
    }

    public PortfolioOverviewResponse getPortfolioOverview(Long customerUserId) {
        Portfolio portfolio = portfolioRepository.findByCustomerUserId(customerUserId)
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found for customer"));

        List<HoldingDto> holdings = holdingRepository
            .findByPortfolioAndQuantityGreaterThan(portfolio, BigDecimal.ZERO)
            .stream()
            .map(this::mapToHoldingDto)
            .sorted(Comparator.comparing(HoldingDto::getSymbol, String.CASE_INSENSITIVE_ORDER))
            .toList();

        PortfolioSummaryDto summary = buildSummary(portfolio, holdings);
        OffsetDateTime lastExecutedTradeAt = tradeRepository
            .findTopByOrderPortfolioOrderByExecutedAtDesc(portfolio)
            .map(Trade::getExecutedAt)
            .orElse(portfolio.getUpdatedAt());

        PortfolioOverviewResponse response = new PortfolioOverviewResponse();
        response.setSummary(summary);
        response.setHoldings(holdings);
        response.setLastExecutedTradeAt(lastExecutedTradeAt);
        return response;
    }

    private HoldingDto mapToHoldingDto(Holding holding) {
        BigDecimal quantity = holding.getQuantity();
        BigDecimal averageCostUsd = safeMoney(holding.getAverageCostUsd());
        BigDecimal currentPrice = resolveCurrentPrice(holding);
        BigDecimal costBasis = averageCostUsd.multiply(quantity);
        BigDecimal currentValueUsd = currentPrice.multiply(quantity);
        BigDecimal gainLossUsd = currentValueUsd.subtract(costBasis);

        HoldingDto dto = new HoldingDto();
        dto.setHoldingId(holding.getHoldingId());
        dto.setInstrumentId(holding.getInstrument().getInstrumentId());
        dto.setSymbol(holding.getInstrument().getSymbol());
        dto.setInstrumentName(holding.getInstrument().getName());
        dto.setQuantity(quantity);
        dto.setAverageCostUsd(scaleMoney(averageCostUsd));
        dto.setCurrentPrice(scaleMoney(currentPrice));
        dto.setCurrentValueUsd(scaleMoney(currentValueUsd));
        dto.setGainLossUsd(scaleMoney(gainLossUsd));
        dto.setGainLossPercent(calculatePercent(gainLossUsd, costBasis));
        return dto;
    }

    private PortfolioSummaryDto buildSummary(Portfolio portfolio, List<HoldingDto> holdings) {
        BigDecimal totalHoldingsValueUsd = holdings.stream()
            .map(HoldingDto::getCurrentValueUsd)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGainLossUsd = holdings.stream()
            .map(HoldingDto::getGainLossUsd)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCostBasisUsd = holdings.stream()
            .map(holding -> holding.getAverageCostUsd().multiply(holding.getQuantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashBalanceUsd = safeMoney(portfolio.getCashBalanceUsd());

        PortfolioSummaryDto summary = new PortfolioSummaryDto();
        summary.setPortfolioId(portfolio.getPortfolioId());
        summary.setCashBalanceUsd(scaleMoney(cashBalanceUsd));
        summary.setBuyingPowerUsd(scaleMoney(cashBalanceUsd));
        summary.setTotalHoldingsValueUsd(scaleMoney(totalHoldingsValueUsd));
        summary.setTotalPortfolioValueUsd(scaleMoney(cashBalanceUsd.add(totalHoldingsValueUsd)));
        summary.setTotalGainLossUsd(scaleMoney(totalGainLossUsd));
        summary.setTotalGainLossPercent(calculatePercent(totalGainLossUsd, totalCostBasisUsd));
        return summary;
    }

    private BigDecimal resolveCurrentPrice(Holding holding) {
        BigDecimal currentPrice = marketPriceService.getCurrentPrice(holding.getInstrument().getSymbol());
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return safeMoney(holding.getAverageCostUsd());
        }
        return currentPrice;
    }

    private BigDecimal calculatePercent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return numerator
            .multiply(ONE_HUNDRED)
            .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return safeMoney(value).setScale(2, RoundingMode.HALF_UP);
    }
}