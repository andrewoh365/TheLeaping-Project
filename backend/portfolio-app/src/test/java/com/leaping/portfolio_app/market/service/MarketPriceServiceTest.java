package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Price;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketPriceServiceTest {

    @Mock
    private PricingService pricingService;

    @InjectMocks
    private MarketPriceService marketPriceService;

    @Test
    void getCurrentQuoteConvertsWithTheFixedCurrencyRate() {
        when(pricingService.getLatestPriceBySymbol("VOD")).thenReturn(Optional.of(price("GBP", "1.35", "100.00")));

        UsdQuote quote = marketPriceService.getCurrentQuote("VOD");

        assertEquals(0, new BigDecimal("100.00").compareTo(quote.nativePrice()));
        assertEquals("GBP", quote.currencyCode());
        assertEquals(0, new BigDecimal("1.35").compareTo(quote.rateToUsd()));
        assertEquals(0, new BigDecimal("135.00000000").compareTo(quote.usdPrice()));
        assertEquals(0, new BigDecimal("135.00000000").compareTo(marketPriceService.getCurrentPrice("VOD")));
    }

    @Test
    void getCurrentQuoteLeavesAUsdPrintUnchanged() {
        when(pricingService.getLatestPriceBySymbol("AAPL")).thenReturn(Optional.of(price("USD", "1", "222.50")));

        UsdQuote quote = marketPriceService.getCurrentQuote("AAPL");

        assertEquals(0, new BigDecimal("222.50000000").compareTo(quote.usdPrice()));
        assertEquals(MarketPriceService.DISPLAY_CURRENCY, quote.currencyCode());
    }

    @Test
    void getCurrentQuoteReturnsNullWhenTheRateIsMissing() {
        when(pricingService.getLatestPriceBySymbol("RELIANCE")).thenReturn(Optional.of(price("INR", null, "800")));

        assertNull(marketPriceService.getCurrentQuote("RELIANCE"));
        assertNull(marketPriceService.toUsd(price("INR", "0", "800")));
    }

    private static Price price(String currencyCode, String rate, String nativePrice) {
        Currency currency = new Currency();
        currency.setCurrencyCode(currencyCode);
        if (rate != null) {
            currency.setCurrentExchangeRateToUsd(new BigDecimal(rate));
        }
        Instrument instrument = new Instrument();
        instrument.setSymbol(currencyCode);
        instrument.setPriceCurrency(currency);
        Price price = new Price();
        price.setInstrument(instrument);
        price.setPriceCurrency(currency);
        price.setPrice(new BigDecimal(nativePrice));
        return price;
    }
}
