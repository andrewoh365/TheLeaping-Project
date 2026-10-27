package com.leaping.portfolio_app.market.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leaping.portfolio_app.market.service.MarketDataIngestionService;
import com.leaping.portfolio_app.market.service.MarketOverride;

/**
 * Testing switch that holds every market open so trades can be tried after hours.
 *
 * - GET /api/market/override - whether the override is available here and whether it's on
 * - PUT /api/market/override - turn it on or off: {"enabled": true}
 *
 * Any signed-in user can use it, but only where trading.market-override.allowed is true
 * (local development). Everywhere else it can't be turned on.
 */
@RestController
@RequestMapping("/api/market/override")
public class MarketOverrideController {
    private static final Logger log = LoggerFactory.getLogger(MarketOverrideController.class);

    private final MarketOverride marketOverride;
    private final MarketDataIngestionService marketDataIngestionService;

    public MarketOverrideController(MarketOverride marketOverride, MarketDataIngestionService marketDataIngestionService) {
        this.marketOverride = marketOverride;
        this.marketDataIngestionService = marketDataIngestionService;
    }

    public record OverrideStatus(boolean allowed, boolean enabled) {
    }

    public record OverrideRequest(boolean enabled) {
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public OverrideStatus getOverride() {
        return status();
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> setOverride(@RequestBody OverrideRequest request, Authentication authentication) {
        if (!marketOverride.isAllowed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "The market override isn't available in this environment."));
        }

        marketOverride.setEnabled(request.enabled());
        log.warn("Market override turned {} by {}", request.enabled() ? "ON" : "OFF", authentication.getName());

        if (request.enabled()) {
            // Stocks stop ticking at the close; price them now so their quotes aren't stale
            try {
                marketDataIngestionService.scheduledRefresh();
            } catch (RuntimeException e) {
                log.warn("Market override is on, but the immediate price refresh failed; the next scheduled tick will catch up", e);
            }
        }
        return ResponseEntity.ok(status());
    }

    private OverrideStatus status() {
        return new OverrideStatus(marketOverride.isAllowed(), marketOverride.isActive());
    }
}
