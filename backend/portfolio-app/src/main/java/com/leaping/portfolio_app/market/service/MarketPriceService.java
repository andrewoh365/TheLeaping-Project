package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Price;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MarketPriceService {

    public static final String DISPLAY_CURRENCY = "USD";

    private final PricingService pricingService;

    public MarketPriceService(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    /**
     * Latest stored quote, in USD. The quote itself comes from the live walk
     * once a price-simulator print exists. Conversion uses the fixed
     * currencies.current_exchange_rate_to_usd rate, not an FX pair.
     */
    @Transactional(readOnly = true)
    public BigDecimal getCurrentPrice(String symbol) {
        UsdQuote quote = getCurrentQuote(symbol);
        return quote == null ? null : quote.usdPrice();
    }

    /**
     * Latest stored quote plus the native print and the rate that produced the dollar price.
     */
    @Transactional(readOnly = true)
    public UsdQuote getCurrentQuote(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return pricingService.getLatestPriceBySymbol(symbol.trim())
                .map(this::describe)
                .orElse(null);
    }

    public BigDecimal toUsd(Price price) {
        UsdQuote quote = describe(price);
        return quote == null ? null : quote.usdPrice();
    }

    private UsdQuote describe(Price price) {
        if (price == null || price.getPrice() == null) {
            return null;
        }
        Currency currency = price.getPriceCurrency();
        BigDecimal rate = currency == null ? null : currency.getCurrentExchangeRateToUsd();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String currencyCode = currency.getCurrencyCode() == null ? DISPLAY_CURRENCY : currency.getCurrencyCode();
        BigDecimal usdPrice = price.getPrice().multiply(rate).setScale(8, RoundingMode.HALF_UP);
        return new UsdQuote(price.getPrice(), currencyCode, rate, usdPrice);
    }
}
