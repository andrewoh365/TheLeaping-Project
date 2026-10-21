package com.leaping.portfolio_app.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
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
                "http://analytics.example"
        );

        doReturn(request).when(restClient).get();
        doReturn(request).when(request).uri(any(URI.class));
        when(request.retrieve()).thenReturn(response);
        when(response.body(String.class)).thenReturn("{}");
    }

    @Test
    void getDashboard_forwardsClientSegmentAndExistingFilters() {
        String result = service.getDashboard(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                "STOCK",
                "NASDAQ",
                "FILLED",
                "FREQUENT"
        );

        MultiValueMap<String, String> query = capturedQuery();

        assertEquals("{}", result);
        assertEquals(
                List.of("2026-09-01"),
                query.get("date_from")
        );
        assertEquals(
                List.of("2026-09-30"),
                query.get("date_to")
        );
        assertEquals(List.of("STOCK"), query.get("asset_type"));
        assertEquals(List.of("NASDAQ"), query.get("market"));
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
                ""
        );

        MultiValueMap<String, String> query = capturedQuery();

        assertTrue(query.get("client_segment") == null);
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
