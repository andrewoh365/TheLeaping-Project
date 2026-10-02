package com.leaping.portfolio_app.analytics.controller;

import com.leaping.portfolio_app.analytics.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ANALYST')")
    public ResponseEntity<String> getDashboard(
            @RequestParam(name = "date_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,

            @RequestParam(name = "date_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,

            @RequestParam(name = "asset_type", required = false)
            String assetType,

            @RequestParam(name = "market", required = false)
            String market,

            @RequestParam(name = "order_status", required = false)
            String orderStatus
    ) {
        String dashboard = analyticsService.getDashboard(
                dateFrom,
                dateTo,
                assetType,
                market,
                orderStatus
        );

        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(dashboard);
    }
}