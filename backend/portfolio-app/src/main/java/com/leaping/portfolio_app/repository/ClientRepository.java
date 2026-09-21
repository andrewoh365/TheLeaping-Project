package com.leaping.portfolio_app.repository;

import com.leaping.portfolio_app.entity.client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<client, Long> {
    Optional<client> findByEmail(String email);
    boolean existsByEmail(String email);
}
