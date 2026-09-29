
package com.leaping.portfolio_app.watchlist.repository;

import com.leaping.portfolio_app.watchlist.entity.WatchlistInstrument;
import com.leaping.portfolio_app.watchlist.entity.WatchlistInstrumentId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WatchlistInstrumentRepository
        extends JpaRepository<WatchlistInstrument, WatchlistInstrumentId> {

    List<WatchlistInstrument> findAllByWatchlist_WatchlistId(Long watchlistId);

    void deleteAllByWatchlist_WatchlistId(Long watchlistId);
}
