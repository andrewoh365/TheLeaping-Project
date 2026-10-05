
package com.leaping.portfolio_app.watchlist.controller;

import com.leaping.portfolio_app.watchlist.dto.CreateWatchlistRequest;
import com.leaping.portfolio_app.watchlist.dto.RenameWatchlistRequest;
import com.leaping.portfolio_app.watchlist.dto.WatchlistResponse;
import com.leaping.portfolio_app.watchlist.dto.WatchlistInstrumentResponse;

import com.leaping.portfolio_app.watchlist.service.WatchlistService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    // CREATE WATCHLIST

    @PostMapping
    public ResponseEntity<WatchlistResponse> createWatchlist(
            @Valid @RequestBody CreateWatchlistRequest request
    ) {
        WatchlistResponse response =
                watchlistService.createWatchlist(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // GET ALL WATCHLISTS

    @GetMapping
    public ResponseEntity<List<WatchlistResponse>> getWatchlists() {

        List<WatchlistResponse> response =
                watchlistService.getWatchlists();

        return ResponseEntity.ok(response);
    }

    // GET ONE WATCHLIST

    @GetMapping("/{watchlistId}")
    public ResponseEntity<WatchlistResponse> getWatchlist(
            @PathVariable Long watchlistId
    ) {
        WatchlistResponse response =
                watchlistService.getWatchlist(watchlistId);

        return ResponseEntity.ok(response);
    }

    // RENAME WATCHLIST

    @PutMapping("/{watchlistId}")
    public ResponseEntity<WatchlistResponse> renameWatchlist(
            @PathVariable Long watchlistId,
            @Valid @RequestBody RenameWatchlistRequest request
    ) {
        WatchlistResponse response =
                watchlistService.renameWatchlist(watchlistId, request);

        return ResponseEntity.ok(response);
    }

    // DELETE WATCHLIST

    @DeleteMapping("/{watchlistId}")
    public ResponseEntity<Void> deleteWatchlist(
            @PathVariable Long watchlistId
    ) {
        watchlistService.deleteWatchlist(watchlistId);

        return ResponseEntity.noContent().build();
    }

    // GET INSTRUMENTS IN A WATCHLIST

    @GetMapping("/{watchlistId}/instruments")
    public ResponseEntity<List<WatchlistInstrumentResponse>>
            getWatchlistInstruments(
                    @PathVariable Long watchlistId
            ) {

        List<WatchlistInstrumentResponse> response =
                watchlistService.getWatchlistInstruments(watchlistId);

        return ResponseEntity.ok(response);
    }

    // ADD INSTRUMENT TO WATCHLIST

    @PostMapping("/{watchlistId}/instruments/{instrumentId}")
    public ResponseEntity<WatchlistInstrumentResponse> addInstrument(
            @PathVariable Long watchlistId,
            @PathVariable Long instrumentId
    ) {
        WatchlistInstrumentResponse response =
                watchlistService.addInstrument(watchlistId, instrumentId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // REMOVE INSTRUMENT FROM WATCHLIST

    @DeleteMapping("/{watchlistId}/instruments/{instrumentId}")
    public ResponseEntity<Void> removeInstrument(
            @PathVariable Long watchlistId,
            @PathVariable Long instrumentId
    ) {
        watchlistService.removeInstrument(watchlistId, instrumentId);

        return ResponseEntity.noContent().build();
    }
}
