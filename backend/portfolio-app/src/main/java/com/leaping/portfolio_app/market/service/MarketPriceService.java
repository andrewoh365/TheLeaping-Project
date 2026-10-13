package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Price;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MarketPriceService {

    private final PricingService pricingService;

    public MarketPriceService(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    /**
     * Latest stored quote, in USD. The quote itself comes from the live walk
     * once a price-simulator print exists.
     */
    @Transactional(readOnly = true)
    public BigDecimal getCurrentPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return pricingService.getLatestPriceBySymbol(symbol.trim())
                .map(this::toUsd)
                .orElse(null);
    }

    private BigDecimal toUsd(Price price) {
        if (price.getPrice() == null) {
            return null;
        }
        Currency currency = price.getPriceCurrency();
        BigDecimal rate = currency == null ? null : currency.getCurrentExchangeRateToUsd();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return price.getPrice().multiply(rate).setScale(8, RoundingMode.HALF_UP);
    }
}
