
package com.leaping.portfolio_app.watchlist.repository;

import com.leaping.portfolio_app.watchlist.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findAllByCustomer_UserIdOrderByCreatedAtDesc(Long customerId);

    Optional<Watchlist> findByWatchlistIdAndCustomer_UserId(Long watchlistId, Long customerId);

    boolean existsByCustomer_UserIdAndWatchlistName(Long customerId, String watchlistName);

    boolean existsByCustomer_UserIdAndWatchlistNameAndWatchlistIdNot(
            Long customerId,
            String watchlistName,
            Long watchlistId
    );
}
