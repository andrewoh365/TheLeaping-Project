package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.market.entity.Price;
import com.leaping.portfolio_app.market.repository.PriceRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MarketPricingService implements PricingService {

    private final PriceRepository priceRepository;

    public MarketPricingService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    @Override
    public Optional<Price> getLatestPriceBySymbol(String symbol) {
        // Once the walk has printed, that series is the quote. A later daily
        // mock bar must not hide it.
        Optional<Price> live = priceRepository
                .findFirstByInstrumentSymbolIgnoreCaseAndProviderNameOrderByPriceTimestampDesc(
                        symbol,
                        PriceMovementSimulator.PROVIDER
                );
        if (live.isPresent()) {
            return live;
        }
        return priceRepository.findFirstByInstrumentSymbolIgnoreCaseOrderByPriceTimestampDesc(symbol);
    }

    @Override
    public List<Price> getPriceHistoryBySymbol(
            String symbol,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        return priceRepository.findByInstrumentSymbolIgnoreCaseAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                symbol,
                from,
                to
        );
    }
}
