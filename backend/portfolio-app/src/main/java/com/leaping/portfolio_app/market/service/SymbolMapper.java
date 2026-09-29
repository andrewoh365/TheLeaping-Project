package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import org.springframework.stereotype.Component;

@Component
public class SymbolMapper {

    public String toYahooSymbol(Instrument instrument) {
        String symbol = instrument.getSymbol();
        InstrumentType type = instrument.getInstrumentType();

        if (type == InstrumentType.FOREX) {
            return symbol.replace("/", "") + "=X";
        }

        if (type == InstrumentType.CRYPTO) {
            return symbol;
        }

        String marketName = instrument.getMarket() == null
                ? ""
                : instrument.getMarket().getMarketName();

        if ("LSE".equalsIgnoreCase(marketName) && !symbol.endsWith(".L")) {
            return symbol + ".L";
        }

        if ("NSE".equalsIgnoreCase(marketName) && !symbol.endsWith(".NS")) {
            return symbol + ".NS";
        }

        return symbol;
    }
}
