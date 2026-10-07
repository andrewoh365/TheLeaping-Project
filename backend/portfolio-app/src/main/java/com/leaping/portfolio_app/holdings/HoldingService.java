package com.leaping.portfolio_app.holdings;
import com.leaping.portfolio_app.holdings.Holding;
import com.leaping.portfolio_app.holdings.HoldingRepository;
import com.leaping.portfolio_app.holdings.HoldingMapper;
import com.leaping.portfolio_app.holdings.HoldingResponse;
import com.leaping.portfolio_app.market.service.MarketPriceService;
import org.springframework.stereotype.Service;
import java.util.List;
import java.math.BigDecimal;

@Service
public class HoldingService {

    private final HoldingRepository holdingRepository;
    private final HoldingMapper mapper;
    private final MarketPriceService marketPriceService;

    public HoldingService(HoldingRepository holdingRepository, HoldingMapper mapper, MarketPriceService marketPriceService) {
        this.holdingRepository = holdingRepository;
        this.mapper = mapper;
        this.marketPriceService = marketPriceService;
    }

    public List<HoldingResponse> getHoldingsByPortfolio(Long portfolioId) {
        return holdingRepository
            .findAllByPortfolio_PortfolioIdOrderByUpdatedAtDesc(portfolioId)
            .stream()
            .map(holding -> enrichHoldingWithMarketData(mapper.toDto(holding), holding))
            .toList();
    }

    public HoldingResponse getHolding(Long portfolioId, Long instrumentId) {
        Holding holding = holdingRepository
            .findByPortfolio_PortfolioIdAndInstrument_InstrumentId(portfolioId, instrumentId)
            .orElseThrow(() -> new RuntimeException("Holding not found"));
        return enrichHoldingWithMarketData(mapper.toDto(holding), holding);
    }

    public List<HoldingResponse> searchHoldingsBySymbol(Long portfolioId, String symbol) {
        return holdingRepository
            .findByPortfolio_PortfolioIdAndInstrument_SymbolContainingIgnoreCase(portfolioId, symbol)
            .stream()
            .map(holding -> enrichHoldingWithMarketData(mapper.toDto(holding), holding))
            .toList();
    }

    /**
     * Enrich a HoldingResponse with real-time market data and P&L calculations
     * @param response Basic holding response from mapper
     * @param holding Original JPA entity for quantity and average cost
     * @return HoldingResponse with currentPrice, currentValueUsd, gainLossUsd, gainLossPercent populated
     */
    private HoldingResponse enrichHoldingWithMarketData(HoldingResponse response, Holding holding) {
        try {
            BigDecimal currentPrice = marketPriceService.getCurrentPrice(response.getSymbol());
            if (currentPrice != null) {
                response.setCurrentPrice(currentPrice);
                
                // Calculate current value: quantity * current price
                BigDecimal currentValueUsd = holding.getQuantity().multiply(currentPrice);
                response.setCurrentValueUsd(currentValueUsd);
                
                // Calculate cost basis: quantity * average cost
                BigDecimal costBasis = holding.getQuantity().multiply(holding.getAverageCostUsd());
                
                // Calculate gain/loss: current value - cost basis
                BigDecimal gainLossUsd = currentValueUsd.subtract(costBasis);
                response.setGainLossUsd(gainLossUsd);
                
                // Calculate gain/loss percent: (gain/loss / cost basis) * 100
                BigDecimal gainLossPercent = BigDecimal.ZERO;
                if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                    gainLossPercent = gainLossUsd
                        .divide(costBasis, 10, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                }
                response.setGainLossPercent(gainLossPercent);
            }
        } catch (Exception e) {
            // If price fetch fails, leave market data fields null
            // This allows graceful degradation if market data service is temporarily unavailable
        }
        return response;
    }
}
