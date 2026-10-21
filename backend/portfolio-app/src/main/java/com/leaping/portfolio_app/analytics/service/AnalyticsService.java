package com.leaping.portfolio_app.analytics.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
public class AnalyticsService {

    private final RestClient restClient;
    private final String analyticsBaseUrl;
    private final String analyticsInternalToken;

    @Autowired
    public AnalyticsService(
            @Value("${analytics.service.base-url:http://localhost:8000}")
            String analyticsBaseUrl,
            @Value("${ANALYTICS_INTERNAL_TOKEN:}")
            String analyticsInternalToken
    ) {
        this(
                RestClient.builder().build(),
                analyticsBaseUrl,
                analyticsInternalToken
        );
    }

    AnalyticsService(
            RestClient restClient,
            String analyticsBaseUrl,
            String analyticsInternalToken
    ) {
        if (analyticsInternalToken == null
                || analyticsInternalToken.isBlank()) {
            throw new IllegalStateException(
                    "ANALYTICS_INTERNAL_TOKEN must be configured"
            );
        }

        this.restClient = restClient;
        this.analyticsBaseUrl = analyticsBaseUrl;
        this.analyticsInternalToken = analyticsInternalToken;
    }

    public String getDashboard(
            LocalDate dateFrom,
            LocalDate dateTo,
            String assetType,
            String market,
            Long instrumentId,
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

        if (instrumentId != null) {
            uriBuilder.queryParam("instrument_id", instrumentId);
        }

        if (orderStatus != null && !orderStatus.isBlank()) {
            uriBuilder.queryParam("order_status", orderStatus);
        }

        if (clientSegment != null && !clientSegment.isBlank()) {
            uriBuilder.queryParam("client_segment", clientSegment);
        }

        try {
            return restClient
                .get()
                .uri(uriBuilder.build().toUri())
                .header(
                    "X-Analytics-Internal-Token",
                    analyticsInternalToken
                )
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException exception) {
            String responseBody = exception.getResponseBodyAsString();
            String reason = responseBody == null || responseBody.isBlank()
                ? exception.getStatusText()
                : responseBody;

            throw new ResponseStatusException(
                exception.getStatusCode(),
                reason,
                exception
            );
            } catch (RestClientException exception) {
                throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Analytics reporting is temporarily unavailable",
                    exception
                );
        }
    }
}