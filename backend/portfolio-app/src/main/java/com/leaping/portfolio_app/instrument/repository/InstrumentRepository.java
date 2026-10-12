
package com.leaping.portfolio_app.instrument.repository;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, Long> {
    Optional<Instrument> findByIsActive(Boolean isActive);

    Optional<Instrument> findByInstrumentId(Long instrumentId);
    
    Optional<Instrument> findBySymbolIgnoreCase(String symbol);

    List<Instrument> findBySymbolStartingWithIgnoreCaseOrNameStartingWithIgnoreCase(String symbolPrefix, String namePrefix);

    List<Instrument> findByIsActiveTrueAndIsTradeableTrue();

    
    default List<Instrument> searchInstruments(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String normalizedQuery = query.trim();
        return findBySymbolStartingWithIgnoreCaseOrNameStartingWithIgnoreCase(normalizedQuery, normalizedQuery);
    }

}


