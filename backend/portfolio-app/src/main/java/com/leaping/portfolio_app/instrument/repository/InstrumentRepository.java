package com.leaping.portfolio_app.instrument.repository;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {
    Optional<Instrument> findByIsActive(Boolean isActive);

    List<Instrument> findByIsActiveTrueAndIsTradeableTrue();

    Optional<Instrument> findBySymbolIgnoreCase(String symbol);

    Optional<Instrument> findBySymbolIgnoreCaseAndIsActiveTrue(String symbol);

    Optional<Instrument> findBySymbol(String symbol);
}
