
package com.leaping.portfolio_app.analytics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

@Service
public class AnalyticsService {

    private static final Logger log =
            LoggerFactory.getLogger(AnalyticsService.class);

    static final String UNAVAILABLE_MESSAGE =
            "Analytics reporting is temporarily unavailable";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
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
        // Fail analytics requests rather than application startup, so a
        // missing token cannot take down unrelated portfolio-app features.
        if (analyticsInternalToken == null
                || analyticsInternalToken.isBlank()) {
            log.warn(
                    "ANALYTICS_INTERNAL_TOKEN is not configured; analytics "
                            + "requests will fail with 503"
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

        if (analyticsInternalToken == null
                || analyticsInternalToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    UNAVAILABLE_MESSAGE
            );
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
            throw toClientException(exception);

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    UNAVAILABLE_MESSAGE,
                    exception
            );
        }
    }

    private ResponseStatusException toClientException(
            RestClientResponseException exception
    ) {
        HttpStatusCode status = exception.getStatusCode();

        // Upstream 401/403 means the service-to-service token is wrong, not
        // that the user's session expired. Passing it through would make the
        // frontend JWT interceptor refresh or log the user out.
        if (status.value() == HttpStatus.UNAUTHORIZED.value()
                || status.value() == HttpStatus.FORBIDDEN.value()) {
            log.error(
                    "Analytics service rejected the internal token "
                            + "(HTTP {}); check ANALYTICS_INTERNAL_TOKEN",
                    status.value()
            );

            return new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Analytics service authentication failed",
                    exception
            );
        }

        if (status.is5xxServerError()) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    UNAVAILABLE_MESSAGE,
                    exception
            );
        }

        return new ResponseStatusException(
                status,
                upstreamDetail(exception),
                exception
        );
    }

    // FastAPI errors look like {"detail": "..."}; validation errors use a
    // list of objects instead, which is not useful to show to a user.
    private String upstreamDetail(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();

        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode detail = jsonMapper
                        .readTree(responseBody)
                        .path("detail");

                if (detail.isString() && !detail.asString().isBlank()) {
                    return detail.asString();
                }
            } catch (JacksonException ignored) {
                // Fall through to the status text.
            }
        }

        return exception.getStatusText();
    }
}
