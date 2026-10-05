package com.leaping.portfolio_app.market.repository;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.entity.Price;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PriceRepository extends JpaRepository<Price, Long> {

    Optional<Price> findFirstByInstrumentSymbolIgnoreCaseOrderByPriceTimestampDesc(String symbol);

    List<Price> findByInstrumentSymbolIgnoreCaseAndPriceTimestampBetweenOrderByPriceTimestampAsc(
            String symbol,
            OffsetDateTime from,
            OffsetDateTime to
    );

    boolean existsByInstrumentAndPriceTimestampAndProviderName(
            Instrument instrument,
            OffsetDateTime priceTimestamp,
            String providerName
    );
}
