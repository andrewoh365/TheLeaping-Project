
package com.leaping.portfolio_app.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> request;

    @Mock
    private RestClient.ResponseSpec response;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(
                restClient,
                "http://analytics.example",
                "test-internal-token"
        );

        lenient().doReturn(request).when(restClient).get();
        lenient().doReturn(request).when(request)
                .header(anyString(), anyString());
        lenient().doReturn(request).when(request)
                .uri(any(URI.class));
        lenient().when(request.retrieve()).thenReturn(response);
        lenient().when(response.body(String.class)).thenReturn("{}");
    }

    @Test
    void getDashboard_forwardsClientSegmentAndExistingFilters() {
        String result = service.getDashboard(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                "STOCK",
                "NASDAQ",
                15L,
                "FILLED",
                "FREQUENT"
        );

        MultiValueMap<String, String> query = capturedQuery();

        assertEquals("{}", result);

        verify(request).header(
                "X-Analytics-Internal-Token",
                "test-internal-token"
        );

        assertEquals(
                List.of("2026-09-01"),
                query.get("date_from")
        );
        assertEquals(
                List.of("2026-09-30"),
                query.get("date_to")
        );
        assertEquals(
                List.of("STOCK"),
                query.get("asset_type")
        );
        assertEquals(
                List.of("NASDAQ"),
                query.get("market")
        );
        assertEquals(
                List.of("15"),
                query.get("instrument_id")
        );
        assertEquals(
                List.of("FILLED"),
                query.get("order_status")
        );
        assertEquals(
                List.of("FREQUENT"),
                query.get("client_segment")
        );
    }

    @Test
    void getDashboard_omitsBlankClientSegment() {
        service.getDashboard(
                null,
                null,
                null,
                null,
                null,
                null,
                ""
        );

        MultiValueMap<String, String> query = capturedQuery();

        assertTrue(query.get("instrument_id") == null);
        assertTrue(query.get("client_segment") == null);
    }

    @Test
    void constructorRejectsMissingInternalToken() {
        assertThrows(
                IllegalStateException.class,
                () -> new AnalyticsService(
                        restClient,
                        "http://analytics.example",
                        ""
                )
        );
    }

    @Test
    void getDashboard_preservesUpstreamNotFoundStatus() {
        HttpClientErrorException notFound =
                HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND,
                        "Not Found",
                        HttpHeaders.EMPTY,
                        "{\"detail\":\"Instrument 999 was not found\"}"
                                .getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8
                );

        when(response.body(String.class)).thenThrow(notFound);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getDashboard(
                        null,
                        null,
                        null,
                        null,
                        999L,
                        null,
                        null
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertTrue(
                exception.getReason().contains(
                        "Instrument 999 was not found"
                )
        );
    }

    @Test
    void getDashboard_preservesUpstreamValidationStatus() {
        HttpClientErrorException validationError =
                HttpClientErrorException.create(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Unprocessable Entity",
                        HttpHeaders.EMPTY,
                        "{\"detail\":\"Invalid dashboard filter\"}"
                                .getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8
                );

        when(response.body(String.class)).thenThrow(validationError);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getDashboard(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getStatusCode()
        );

        assertTrue(
                exception.getReason().contains(
                        "Invalid dashboard filter"
                )
        );
    }

    @Test
    void getDashboard_mapsReportingConnectionFailureTo503() {
        when(response.body(String.class)).thenThrow(
                new RestClientException("connection refused")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getDashboard(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        assertEquals(
                "Analytics reporting is temporarily unavailable",
                exception.getReason()
        );
    }

    private MultiValueMap<String, String> capturedQuery() {
        ArgumentCaptor<URI> uriCaptor =
                ArgumentCaptor.forClass(URI.class);

        verify(request).uri(uriCaptor.capture());

        return UriComponentsBuilder
                .fromUri(uriCaptor.getValue())
                .build()
                .getQueryParams();
    }
}
