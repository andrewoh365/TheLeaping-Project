package com.leaping.portfolio_app.analytics.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

@Service
public class AnalyticsService {

    private final RestClient restClient;
    private final String analyticsBaseUrl;

    @Autowired
    public AnalyticsService(
            @Value("${analytics.service.base-url:http://localhost:8000}")
            String analyticsBaseUrl
    ) {
        this(RestClient.builder().build(), analyticsBaseUrl);
    }

    AnalyticsService(
            RestClient restClient,
            String analyticsBaseUrl
    ) {
        this.restClient = restClient;
        this.analyticsBaseUrl = analyticsBaseUrl;
    }

    public String getDashboard(
            LocalDate dateFrom,
            LocalDate dateTo,
            String assetType,
            String market,
            String orderStatus,
            String clientSegment
    ) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(analyticsBaseUrl)
                .path("/analytics/dashboard");

        if (dateFrom != null) {
            uriBuilder.queryParam("date_from", dateFrom);
        }

        if (dateTo != null) {
            uriBuilder.queryParam("date_to", dateTo);
        }

        if (assetType != null && !assetType.isBlank()) {
            uriBuilder.queryParam("asset_type", assetType);
        }

        if (market != null && !market.isBlank()) {
            uriBuilder.queryParam("market", market);
        }

        if (orderStatus != null && !orderStatus.isBlank()) {
            uriBuilder.queryParam("order_status", orderStatus);
        }

        if (clientSegment != null && !clientSegment.isBlank()) {
            uriBuilder.queryParam("client_segment", clientSegment);
        }

        return restClient
                .get()
                .uri(uriBuilder.build().toUri())
                .retrieve()
                .body(String.class);
    }
}