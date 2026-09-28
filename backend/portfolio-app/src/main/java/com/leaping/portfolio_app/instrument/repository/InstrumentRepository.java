package com.leaping.portfolio_app.instrument.repository;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {
    Optional<Instrument> findByIsActive(Boolean isActive);
    Optional<Instrument> findBySymbol(String symbol);
}
