package com.leaping.portfolio_app.market.service;

import org.springframework.stereotype.Component;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

@Component
public class YahooHistoricalPriceProvider {

    public List<MarketPricePoint> getHistoricalClosePrices(
            String yahooSymbol,
            LocalDate from,
            LocalDate to
    ) {
        List<MarketPricePoint> points = new ArrayList<>();

        Calendar fromCal = new GregorianCalendar(
                from.getYear(),
                from.getMonthValue() - 1,
                from.getDayOfMonth()
        );

        Calendar toCal = new GregorianCalendar(
                to.getYear(),
                to.getMonthValue() - 1,
                to.getDayOfMonth()
        );

        try {
            Stock stock = YahooFinance.get(yahooSymbol, true);
            if (stock == null) {
                return points;
            }

            List<HistoricalQuote> history = stock.getHistory(fromCal, toCal, Interval.DAILY);
            for (HistoricalQuote quote : history) {
                if (quote == null || quote.getClose() == null || quote.getDate() == null) {
                    continue;
                }

                BigDecimal close = quote.getClose();
                LocalDate date = LocalDate.of(
                        quote.getDate().get(Calendar.YEAR),
                        quote.getDate().get(Calendar.MONTH) + 1,
                        quote.getDate().get(Calendar.DAY_OF_MONTH)
                );
                OffsetDateTime timestamp = date.atStartOfDay().atOffset(ZoneOffset.UTC);
                points.add(new MarketPricePoint(timestamp, close));
            }
        } catch (IOException ignored) {
            // Price sync is best-effort for mock historical data.
        }

        return points;
    }
}
