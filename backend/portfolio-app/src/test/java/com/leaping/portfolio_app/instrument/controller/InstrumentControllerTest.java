package com.leaping.portfolio_app.instrument.controller;

import com.leaping.portfolio_app.instrument.dto.InstrumentDTO;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.instrument.service.InstrumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InstrumentControllerTest {

    @Mock
    private InstrumentService instrumentService;

    private MockMvc mockMvc;

    private Instrument appleStock;
    private Instrument microsoftStock;
    private Market market;
    private Currency currency;

    @BeforeEach
    void setUp() {
        InstrumentController controller = new InstrumentController(instrumentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        // Create test data
        market = new Market();
        market.setMarketId(1L);

        currency = new Currency();
        currency.setCurrencyCode("USD");

        appleStock = createTestInstrument(1L, "AAPL", "Apple Inc.");
        microsoftStock = createTestInstrument(2L, "MSFT", "Microsoft Corporation");
    }

    /**
     * Test: searchInstruments endpoint returns matching instruments
     */
    @Test
    void searchInstruments_withValidQuery_shouldReturn200AndResults() throws Exception {
        String query = "AAPL";
        List<Instrument> instruments = Arrays.asList(appleStock);
        when(instrumentService.searchInstruments(query)).thenReturn(instruments);

        mockMvc.perform(get("/api/auth/instrument/search")
                .param("query", query)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc."))
                .andExpect(jsonPath("$[0].instrumentId").value(1))
                .andExpect(jsonPath("$[0].isTradeable").value(true))
                .andExpect(jsonPath("$[0].isActive").value(true));

        verify(instrumentService).searchInstruments(query);
    }

    /**
     * Test: searchInstruments returns multiple matching results
     */
    @Test
    void searchInstruments_withMultipleMatches_shouldReturnAllResults() throws Exception {
        String query = "Inc";
        List<Instrument> instruments = Arrays.asList(appleStock, microsoftStock);
        when(instrumentService.searchInstruments(query)).thenReturn(instruments);

        mockMvc.perform(get("/api/auth/instrument/search")
                .param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[1].symbol").value("MSFT"));

        verify(instrumentService).searchInstruments(query);
    }

    /**
     * Test: searchInstruments returns empty list when no matches
     */
    @Test
    void searchInstruments_withNoMatches_shouldReturn200AndEmptyList() throws Exception {
        String query = "NONEXISTENT";
        when(instrumentService.searchInstruments(query)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/auth/instrument/search")
                .param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(instrumentService).searchInstruments(query);
    }

    /**
     * Test: searchInstruments with case-insensitive query
     */
    @Test
    void searchInstruments_withLowercaseQuery_shouldReturnMatches() throws Exception {
        String query = "apple";
        List<Instrument> instruments = Arrays.asList(appleStock);
        when(instrumentService.searchInstruments(query)).thenReturn(instruments);

        mockMvc.perform(get("/api/auth/instrument/search")
                .param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));

        verify(instrumentService).searchInstruments(query);
    }

    /**
     * Test: getInstrumentById returns single instrument
     */
    @Test
    void getInstrumentById_withValidId_shouldReturn200AndInstrument() throws Exception {
        Long instrumentId = 1L;
        when(instrumentService.getInstrumentById(instrumentId))
                .thenReturn(Optional.of(appleStock));

        mockMvc.perform(get("/api/auth/instrument/{id}", instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc."))
                .andExpect(jsonPath("$.instrumentId").value(1));

        verify(instrumentService).getInstrumentById(instrumentId);
    }

    /**
     * Test: getInstrumentById returns 404 for invalid ID
     */
    @Test
    void getInstrumentById_withInvalidId_shouldReturn404() throws Exception {
        Long instrumentId = 999L;
        when(instrumentService.getInstrumentById(instrumentId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/instrument/{id}", instrumentId))
                .andExpect(status().isNotFound());

        verify(instrumentService).getInstrumentById(instrumentId);
    }

    /**
     * Test: searchInstruments response includes all instrument details
     */
    @Test
    void searchInstruments_shouldIncludeAllInstrumentFields() throws Exception {
        String query = "AAPL";
        List<Instrument> instruments = Arrays.asList(appleStock);
        when(instrumentService.searchInstruments(query)).thenReturn(instruments);

        mockMvc.perform(get("/api/auth/instrument/search")
                .param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instrumentId").exists())
                .andExpect(jsonPath("$[0].symbol").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].instrumentType").exists())
                .andExpect(jsonPath("$[0].priceCurrencyCode").exists())
                .andExpect(jsonPath("$[0].isTradeable").exists())
                .andExpect(jsonPath("$[0].isActive").exists());
    }

    /**
     * Test: searchInstruments response content type is JSON
     */
    @Test
    void searchInstruments_shouldReturnJsonContentType() throws Exception {
        String query = "AAPL";
        when(instrumentService.searchInstruments(query))
                .thenReturn(Arrays.asList(appleStock));

        mockMvc.perform(get("/api/auth/instrument/search")
                .param("query", query))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    /**
     * Test: getInstrumentById response includes all fields
     */
    @Test
    void getInstrumentById_shouldIncludeAllFields() throws Exception {
        Long instrumentId = 1L;
        when(instrumentService.getInstrumentById(instrumentId))
                .thenReturn(Optional.of(appleStock));

        mockMvc.perform(get("/api/auth/instrument/{id}", instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value(1L))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc."))
                .andExpect(jsonPath("$.instrumentType").value("STOCK"))
                .andExpect(jsonPath("$.priceCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.isTradeable").value(true))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    /**
     * Test: searchInstruments calls service method exactly once
     */
    @Test
    void searchInstruments_shouldCallServiceOnce() throws Exception {
        String query = "TEST";
        when(instrumentService.searchInstruments(query))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/auth/instrument/search")
                .param("query", query));

        verify(instrumentService, times(1)).searchInstruments(query);
        verifyNoMoreInteractions(instrumentService);
    }

    
    private Instrument createTestInstrument(Long id, String symbol, String name) {
        Instrument instrument = new Instrument(
                market,
                symbol,
                name,
                InstrumentType.STOCK,
                currency
        );
        instrument.setInstrumentId(id);
        instrument.setIsActive(true);
        instrument.setIsTradeable(true);
        instrument.setCreatedAt(OffsetDateTime.now());
        instrument.setUpdatedAt(OffsetDateTime.now());
        return instrument;
    }
}
