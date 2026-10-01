package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.market.entity.Market;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Service
public class MarketHoursService {

    public MarketStatus getMarketStatus(Market market) {
        ZoneId zone = ZoneId.of(market.getTimezone());
        ZonedDateTime now = OffsetDateTime.now(ZoneOffset.UTC).atZoneSameInstant(zone);

        LocalTime openTime = market.getOpenTime();
        LocalTime closeTime = market.getCloseTime();

        if (openTime == null || closeTime == null) {
            return new MarketStatus(
                    market.getMarketName(),
                    market.getTimezone(),
                    now.toOffsetDateTime(),
                    true,
                    true,
                    null,
                    null
            );
        }

        boolean isOpen = isWithinWindow(now.toLocalTime(), openTime, closeTime);
        OffsetDateTime nextOpen = computeNext(openTime, now);
        OffsetDateTime nextClose = computeNext(closeTime, now);

        return new MarketStatus(
                market.getMarketName(),
                market.getTimezone(),
                now.toOffsetDateTime(),
                isOpen,
                false,
                nextOpen,
                nextClose
        );
    }

    private boolean isWithinWindow(LocalTime now, LocalTime open, LocalTime close) {
        if (close.isAfter(open)) {
            return !now.isBefore(open) && now.isBefore(close);
        }

        return !now.isBefore(open) || now.isBefore(close);
    }

    private OffsetDateTime computeNext(LocalTime target, ZonedDateTime now) {
        LocalDate date = now.toLocalDate();
        LocalDateTime localDateTime = LocalDateTime.of(date, target);
        ZonedDateTime candidate = localDateTime.atZone(now.getZone());

        if (!candidate.isAfter(now)) {
            candidate = LocalDateTime.of(date.plusDays(1), target).atZone(now.getZone());
        }

        return candidate.toOffsetDateTime();
    }
}
