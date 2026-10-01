package com.leaping.portfolio_app.market.service;

import com.leaping.portfolio_app.market.entity.Price;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PricingService {

    Optional<Price> getLatestPriceBySymbol(String symbol);

    List<Price> getPriceHistoryBySymbol(
            String symbol,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
