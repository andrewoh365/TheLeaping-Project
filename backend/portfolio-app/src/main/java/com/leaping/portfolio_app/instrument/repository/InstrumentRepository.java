package com.leaping.portfolio_app.instrument.repository;

import com.leaping.portfolio_app.instrument.entity.Instrument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstrumentRepository
        extends JpaRepository<Instrument, Long> {

    Optional<Instrument> findByIsActive(Boolean isActive);

    Optional<Instrument> findByInstrumentId(Long instrumentId);

    Optional<Instrument> findBySymbol(String symbol);

    Optional<Instrument> findBySymbolIgnoreCase(String symbol);

    Optional<Instrument>
        findBySymbolIgnoreCaseAndIsActiveTrue(
            String symbol
        );

    List<Instrument>
        findByIsActiveTrueAndIsTradeableTrue();

    List<Instrument>
        findByNameContainingIgnoreCase(
            String query
        );

    @Query("""
        SELECT DISTINCT i
        FROM Instrument i
        WHERE :query IS NOT NULL
          AND TRIM(:query) <> ''
          AND (
              LOWER(i.symbol)
                  LIKE LOWER(CONCAT('%', :query, '%'))
              OR
              LOWER(i.name)
                  LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    List<Instrument> searchInstruments(
        @Param("query") String query
    );
}
