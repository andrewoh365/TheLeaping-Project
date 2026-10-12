package com.leaping.portfolio_app.market.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class MarketPriceService {

    private final PricingService pricingService;

    public MarketPriceService(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    public BigDecimal getCurrentPrice(String symbol) {
        return pricingService.getLatestPriceBySymbol(symbol)
                .map(price -> price.getPrice())
                .orElse(null);
    }
}