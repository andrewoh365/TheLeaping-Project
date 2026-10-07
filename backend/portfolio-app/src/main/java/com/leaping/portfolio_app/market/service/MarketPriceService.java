package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.market.repository.PriceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class MarketPriceService {

    private final PriceRepository priceRepository;

    public MarketPriceService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    public BigDecimal getCurrentPrice(String symbol) {
        return priceRepository.findFirstByInstrumentSymbolIgnoreCaseOrderByPriceTimestampDesc(symbol)
                .map(price -> price.getPrice())
                .orElse(null);
    }
}