package com.leaping.portfolio_app.holdings;
import org.springframework.stereotype.Component;
import com.leaping.portfolio_app.holdings.Holding;
import com.leaping.portfolio_app.holdings.HoldingResponse;

@Component
public class HoldingMapper {
    public HoldingResponse toDto(Holding holding) {
        return new HoldingResponse(
            holding.getInstrument().getSymbol(),
            holding.getInstrument().getName(),
            holding.getQuantity(),
            holding.getAverageCostUsd()
        );
    }
}