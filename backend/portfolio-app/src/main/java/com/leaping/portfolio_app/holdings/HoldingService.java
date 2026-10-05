package com.leaping.portfolio_app.holdings;
import com.leaping.portfolio_app.holdings.Holding;
import com.leaping.portfolio_app.holdings.HoldingRepository;
import com.leaping.portfolio_app.holdings.HoldingMapper;
import com.leaping.portfolio_app.holdings.HoldingResponse;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HoldingService {

    private final HoldingRepository holdingRepository;
    private final HoldingMapper mapper;

    public HoldingService(HoldingRepository holdingRepository, HoldingMapper mapper) {
        this.holdingRepository = holdingRepository;
        this.mapper = mapper;
    }

    public List<HoldingResponse> getHoldingsByPortfolio(Long portfolioId) {
        return holdingRepository
            .findAllByPortfolio_PortfolioIdOrderByUpdatedAtDesc(portfolioId)
            .stream()
            .map(mapper::toDto)
            .toList();
    }

    public HoldingResponse getHolding(Long portfolioId, Long instrumentId) {
        Holding holding = holdingRepository
            .findByPortfolio_PortfolioIdAndInstrument_InstrumentId(portfolioId, instrumentId)
            .orElseThrow(() -> new RuntimeException("Holding not found"));
        return mapper.toDto(holding);
    }

    public List<HoldingResponse> searchHoldingsBySymbol(Long portfolioId, String symbol) {
        return holdingRepository
            .findByPortfolio_PortfolioIdAndInstrument_SymbolContainingIgnoreCase(portfolioId, symbol)
            .stream()
            .map(mapper::toDto)
            .toList();
    } 
}
