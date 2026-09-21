package com.leaping.portfolio_app.repository;

import com.leaping.portfolio_app.entity.instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository

public interface InstrumentRepository extends JpaRepository<instrument, Long> {
    Optional<instrument> findByIsActive(Boolean isActive);

}
