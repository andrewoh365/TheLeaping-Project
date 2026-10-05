package com.leaping.portfolio_app.holdings;
import org.springframework.stereotype.Component;
import com.leaping.portfolio_app.holdings.Holding;
import com.leaping.portfolio_app.holdings.HoldingResponse;

@Component
public class HoldingMapper {
    
    /**
     * Map Holding entity to HoldingResponse DTO with basic information
     * Note: Market data fields (currentPrice, currentValueUsd, gainLossUsd, gainLossPercent) 
     * must be populated separately with real-time market data
     */
    public HoldingResponse toDto(Holding holding) {
        return new HoldingResponse(
            holding.getHoldingId(),
            holding.getInstrument().getInstrumentId(),
            holding.getInstrument().getSymbol(),
            holding.getInstrument().getName(),
            holding.getQuantity(),
            holding.getAverageCostUsd()
        );
    }
}