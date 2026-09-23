package com.leaping.portfolio_app.repository;

import com.leaping.portfolio_app.auth.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.List;

@Repository

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {
    Optional<Instrument> findByIsActive(Boolean isActive);
    List<Instrument> findByNameContainingIgnoreCase(String query);

}


