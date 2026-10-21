package com.leaping.portfolio_app.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.leaping.portfolio_app.instrument.enums.InstrumentType;

class BidAskQuoteTest {

    @Test
    void stockQuoteShouldStraddleTheMidByHalfTheSpread() {
        BidAskQuote quote = BidAskQuote.fromMid(new BigDecimal("200.00"), InstrumentType.STOCK);

        assertEquals(new BigDecimal("199.96000000"), quote.bid());
        assertEquals(new BigDecimal("200.04000000"), quote.ask());
        assertEquals(new BigDecimal("0.08000000"), quote.spread());
    }

    @Test
    void buyersPayTheAskAndSellersReceiveTheBid() {
        BidAskQuote quote = BidAskQuote.fromMid(new BigDecimal("100.00"), InstrumentType.STOCK);

        assertEquals(quote.ask(), quote.priceFor(true));
        assertEquals(quote.bid(), quote.priceFor(false));
    }

    @Test
    void cryptoShouldHaveAWiderSpreadThanFx() {
        BigDecimal mid = new BigDecimal("1.00");

        BigDecimal crypto = BidAskQuote.fromMid(mid, InstrumentType.CRYPTO).spread();
        BigDecimal fx = BidAskQuote.fromMid(mid, InstrumentType.FOREX).spread();

        assertEquals(new BigDecimal("0.00200000"), crypto);
        assertEquals(new BigDecimal("0.00020000"), fx);
    }

    @Test
    void noQuoteWithoutAUsablePrice() {
        assertNull(BidAskQuote.fromMid(null, InstrumentType.STOCK));
        assertNull(BidAskQuote.fromMid(BigDecimal.ZERO, InstrumentType.STOCK));
    }
}
