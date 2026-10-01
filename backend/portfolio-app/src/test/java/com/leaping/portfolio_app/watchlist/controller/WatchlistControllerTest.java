
package com.leaping.portfolio_app.watchlist.controller;

import com.leaping.portfolio_app.watchlist.dto.CreateWatchlistRequest;
import com.leaping.portfolio_app.watchlist.dto.WatchlistResponse;
import com.leaping.portfolio_app.watchlist.dto.RenameWatchlistRequest;
import com.leaping.portfolio_app.watchlist.dto.WatchlistInstrumentResponse;
import com.leaping.portfolio_app.watchlist.service.WatchlistService;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class WatchlistControllerTest {

    @Mock
    private WatchlistService watchlistService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        WatchlistController controller =
                new WatchlistController(watchlistService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void createWatchlist_shouldReturn201Created() throws Exception {

        // Arrange: Prepare the service response
        OffsetDateTime now = OffsetDateTime.now();

        WatchlistResponse response = new WatchlistResponse(
                10L,
                "Tech Stocks",
                now,
                now
        );

        when(watchlistService.createWatchlist(
                any(CreateWatchlistRequest.class)
        )).thenReturn(response);

        // Act and Assert: Send POST request
        mockMvc.perform(post("/api/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "watchlistName": "Tech Stocks"
                                }
                                """))

                .andExpect(status().isCreated())

                .andExpect(jsonPath("$.watchlistId").value(10))

                .andExpect(jsonPath("$.watchlistName")
                        .value("Tech Stocks"));

        // Verify the controller passed the correct request to the service
        verify(watchlistService).createWatchlist(
                argThat(request ->
                        "Tech Stocks".equals(request.getWatchlistName())
                )
        );
    }
    
    @Test
    void getWatchlists_shouldReturnAllWatchlists() throws Exception {

        // Arrange: Prepare two watchlist responses
        OffsetDateTime now = OffsetDateTime.now();

        WatchlistResponse watchlist1 = new WatchlistResponse(
                10L,
                "Tech Stocks",
                now,
                now
        );

        WatchlistResponse watchlist2 = new WatchlistResponse(
                11L,
                "Crypto Favorites",
                now,
                now
        );

        when(watchlistService.getWatchlists())
                .thenReturn(List.of(watchlist1, watchlist2));

        // Act and Assert: Send GET request
        mockMvc.perform(get("/api/watchlists"))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.length()").value(2))

                .andExpect(jsonPath("$[0].watchlistId").value(10))
                .andExpect(jsonPath("$[0].watchlistName").value("Tech Stocks"))

                .andExpect(jsonPath("$[1].watchlistId").value(11))
                .andExpect(jsonPath("$[1].watchlistName").value("Crypto Favorites"));

        // Verify the correct service method was called
        verify(watchlistService).getWatchlists();
    }
    
    @Test
    void getWatchlist_shouldReturnSingleWatchlist() throws Exception {

        // Arrange: Prepare the watchlist response
        OffsetDateTime now = OffsetDateTime.now();

        WatchlistResponse response = new WatchlistResponse(
                10L,
                "Tech Stocks",
                now,
                now
        );

        when(watchlistService.getWatchlist(10L))
                .thenReturn(response);

        // Act and Assert: Send GET request
        mockMvc.perform(get("/api/watchlists/10"))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.watchlistId").value(10))

                .andExpect(jsonPath("$.watchlistName")
                        .value("Tech Stocks"));

        // Verify the correct watchlist ID was passed to the service
        verify(watchlistService).getWatchlist(10L);
    }
    
    @Test
    void renameWatchlist_shouldReturnUpdatedWatchlist() throws Exception {

        // Arrange: Prepare the updated watchlist response
        OffsetDateTime now = OffsetDateTime.now();

        WatchlistResponse response = new WatchlistResponse(
                10L,
                "Favorite Stocks",
                now,
                now
        );

        when(watchlistService.renameWatchlist(
                eq(10L),
                any(RenameWatchlistRequest.class)
        )).thenReturn(response);

        // Act and Assert: Send PUT request
        mockMvc.perform(put("/api/watchlists/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "watchlistName": "Favorite Stocks"
                                }
                                """))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.watchlistId").value(10))

                .andExpect(jsonPath("$.watchlistName")
                        .value("Favorite Stocks"));

        // Verify the controller passed the correct ID and new name
        verify(watchlistService).renameWatchlist(
                eq(10L),
                argThat(request ->
                        "Favorite Stocks".equals(request.getWatchlistName())
                )
        );
    }

    @Test
    void deleteWatchlist_shouldReturn204NoContent() throws Exception {

        // Act and Assert: Send DELETE request
        mockMvc.perform(delete("/api/watchlists/10"))

                .andExpect(status().isNoContent())

                .andExpect(content().string(""));

        // Verify the correct watchlist ID was passed to the service
        verify(watchlistService).deleteWatchlist(10L);
    }
    
    @Test
    void getWatchlistInstruments_shouldReturnSavedInstruments() throws Exception {

        // Arrange: Prepare an instrument response
        WatchlistInstrumentResponse instrument =
                new WatchlistInstrumentResponse(
                        25L,
                        "AAPL",
                        "Apple Inc.",
                        InstrumentType.STOCK
                );

        when(watchlistService.getWatchlistInstruments(10L))
                .thenReturn(List.of(instrument));

        // Act and Assert: Send GET request
        mockMvc.perform(get("/api/watchlists/10/instruments"))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.length()").value(1))

                .andExpect(jsonPath("$[0].instrumentId").value(25))

                .andExpect(jsonPath("$[0].symbol").value("AAPL"))

                .andExpect(jsonPath("$[0].name").value("Apple Inc."))

                .andExpect(jsonPath("$[0].instrumentType").value("STOCK"));

        // Verify the correct watchlist ID was passed to the service
        verify(watchlistService).getWatchlistInstruments(10L);
    }

    @Test
    void addInstrument_shouldReturn201Created() throws Exception {

        // Arrange: Prepare the saved instrument response
        WatchlistInstrumentResponse response =
                new WatchlistInstrumentResponse(
                        25L,
                        "AAPL",
                        "Apple Inc.",
                        InstrumentType.STOCK
                );

        when(watchlistService.addInstrument(10L, 25L))
                .thenReturn(response);

        // Act and Assert: Send POST request
        mockMvc.perform(post("/api/watchlists/10/instruments/25"))

                .andExpect(status().isCreated())

                .andExpect(jsonPath("$.instrumentId").value(25))

                .andExpect(jsonPath("$.symbol").value("AAPL"))

                .andExpect(jsonPath("$.name").value("Apple Inc."))

                .andExpect(jsonPath("$.instrumentType").value("STOCK"));

        // Verify both IDs were passed to the service
        verify(watchlistService).addInstrument(10L, 25L);
    }
    
    @Test
    void removeInstrument_shouldReturn204NoContent() throws Exception {

        // Act and Assert: Send DELETE request
        mockMvc.perform(delete("/api/watchlists/10/instruments/25"))

                .andExpect(status().isNoContent())

                .andExpect(content().string(""));

        // Verify the correct IDs were passed to the service
        verify(watchlistService).removeInstrument(10L, 25L);
    }

    @Test
    void createWatchlist_shouldRejectBlankName() throws Exception {

        // Act and Assert: Send an invalid POST request
        mockMvc.perform(post("/api/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "watchlistName": "   "
                                }
                                """))

                .andExpect(status().isBadRequest());

        // Verify the invalid request never reached the service
        verifyNoInteractions(watchlistService);
    }
    
    @Test
    void createWatchlist_shouldRejectNameOver100Characters() throws Exception {

        // Arrange: Create a name containing 101 characters
        String longName = "A".repeat(101);

        String requestBody = """
                {
                    "watchlistName": "%s"
                }
                """.formatted(longName);

        // Act and Assert: Send invalid POST request
        mockMvc.perform(post("/api/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))

                .andExpect(status().isBadRequest());

        // Verify the invalid request never reached the service
        verifyNoInteractions(watchlistService);
    }

    @Test
    void renameWatchlist_shouldRejectBlankName() throws Exception {

        // Act and Assert: Send invalid PUT request
        mockMvc.perform(put("/api/watchlists/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "watchlistName": "   "
                                }
                                """))

                .andExpect(status().isBadRequest());

        // Verify the invalid request never reached the service
        verifyNoInteractions(watchlistService);
    }
    
    @Test
    void getWatchlist_shouldReturn404WhenNotFound() throws Exception {

        // Arrange: Simulate the service throwing a 404 error
        when(watchlistService.getWatchlist(999L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Watchlist not found"
                ));

        // Act and Assert: Request a nonexistent watchlist
        mockMvc.perform(get("/api/watchlists/999"))

                .andExpect(status().isNotFound());

        // Verify the correct ID was passed to the service
        verify(watchlistService).getWatchlist(999L);
    }

    @Test
    void createWatchlist_shouldReturn409ForDuplicateName() throws Exception {

        // Arrange: Simulate a duplicate-name error
        when(watchlistService.createWatchlist(
                any(CreateWatchlistRequest.class)
        )).thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A watchlist with this name already exists"
        ));

        // Act and Assert: Attempt to create a duplicate watchlist
        mockMvc.perform(post("/api/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "watchlistName": "Tech Stocks"
                                }
                                """))

                .andExpect(status().isConflict());

        // Verify the request reached the service correctly
        verify(watchlistService).createWatchlist(
                argThat(request ->
                        "Tech Stocks".equals(request.getWatchlistName())
                )
        );
    }
    
    @Test
    void renameWatchlist_shouldRejectNameOver100Characters() throws Exception {

        // Arrange: Create a name containing 101 characters
        String longName = "A".repeat(101);

        String requestBody = """
                {
                    "watchlistName": "%s"
                }
                """.formatted(longName);

        // Act and Assert: Send invalid PUT request
        mockMvc.perform(put("/api/watchlists/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))

                .andExpect(status().isBadRequest());

        // Verify the invalid request never reached the service
        verifyNoInteractions(watchlistService);
    }

    @Test
    void createWatchlist_shouldRejectMissingName() throws Exception {

        // Act and Assert: Send request without watchlistName
        mockMvc.perform(post("/api/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))

                .andExpect(status().isBadRequest());

        // Verify the invalid request never reached the service
        verifyNoInteractions(watchlistService);
    }
}
