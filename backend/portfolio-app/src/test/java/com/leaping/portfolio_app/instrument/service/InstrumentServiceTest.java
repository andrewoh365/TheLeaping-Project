package com.leaping.portfolio_app.service;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.repository.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @InjectMocks
    private InstrumentService instrumentService;

    private Instrument appleStock;
    private Instrument microsoftStock;
    private Instrument googleStock;
    private Market market;
    private Currency currency;

    @BeforeEach
    void setUp() {
        market = new Market();
        market.setMarketId(1L);

        currency = new Currency();
        currency.setCurrencyCode("USD");

        appleStock = createInstrument(1L, "AAPL", "Apple Inc.", market, currency);
        microsoftStock = createInstrument(2L, "MSFT", "Microsoft Corporation", market, currency);
        googleStock = createInstrument(3L, "GOOGL", "Alphabet Inc.", market, currency);
    }

    /**
     * Test: searchInstruments returns results matching symbol
     */
    @Test
    void searchInstruments_withSymbolQuery_shouldReturnMatchingInstruments() {
        String query = "AAPL";
        List<Instrument> expectedResults = Arrays.asList(appleStock);
        when(instrumentRepository.searchInstruments(query)).thenReturn(expectedResults);

        List<Instrument> results = instrumentService.searchInstruments(query);

       assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("AAPL", results.get(0).getSymbol());
        assertEquals("Apple Inc.", results.get(0).getName());
        verify(instrumentRepository).searchInstruments(query);
    }

    /**
     * Test: searchInstruments returns results matching name
     */
    @Test
    void searchInstruments_withNameQuery_shouldReturnMatchingInstruments() {
        String query = "Microsoft";
        List<Instrument> expectedResults = Arrays.asList(microsoftStock);
        when(instrumentRepository.searchInstruments(query)).thenReturn(expectedResults);

        List<Instrument> results = instrumentService.searchInstruments(query);

       assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("MSFT", results.get(0).getSymbol());
        assertEquals("Microsoft Corporation", results.get(0).getName());
        verify(instrumentRepository).searchInstruments(query);
    }

    /**
     * Test: searchInstruments is case-insensitive
     */
    @Test
    void searchInstruments_withCaseInsensitiveQuery_shouldReturnMatchingInstruments() {
        String query = "google"; // lowercase
        List<Instrument> expectedResults = Arrays.asList(googleStock);
        when(instrumentRepository.searchInstruments(query)).thenReturn(expectedResults);

        List<Instrument> results = instrumentService.searchInstruments(query);

       assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("GOOGL", results.get(0).getSymbol());
        verify(instrumentRepository).searchInstruments(query);
    }

    /**
     * Test: searchInstruments returns multiple matches
     */
    @Test
    void searchInstruments_withMultipleMatches_shouldReturnAllMatches() {
        String query = "Inc"; // matches both "Apple Inc." and "Alphabet Inc."
        List<Instrument> expectedResults = Arrays.asList(appleStock, googleStock);
        when(instrumentRepository.searchInstruments(query)).thenReturn(expectedResults);

        List<Instrument> results = instrumentService.searchInstruments(query);

       assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(i -> "AAPL".equals(i.getSymbol())));
        assertTrue(results.stream().anyMatch(i -> "GOOGL".equals(i.getSymbol())));
        verify(instrumentRepository).searchInstruments(query);
    }

    /**
     * Test: searchInstruments returns empty list when no matches found
     */
    @Test
    void searchInstruments_withNoMatches_shouldReturnEmptyList() {
        String query = "NONEXISTENT";
        when(instrumentRepository.searchInstruments(query)).thenReturn(Collections.emptyList());

        List<Instrument> results = instrumentService.searchInstruments(query);

       assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(instrumentRepository).searchInstruments(query);
    }

    /**
     * Test: searchInstruments with empty string query
     */
    @Test
    void searchInstruments_withEmptyQuery_shouldReturnEmptyList() {
        String query = "";
        when(instrumentRepository.searchInstruments(query)).thenReturn(Collections.emptyList());

        List<Instrument> results = instrumentService.searchInstruments(query);

       assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(instrumentRepository).searchInstruments(query);
    }

    /**
     * Test: searchInstruments with null query should handle gracefully
     */
    @Test
    void searchInstruments_withNullQuery_shouldReturnEmptyList() {
        when(instrumentRepository.searchInstruments(null)).thenReturn(Collections.emptyList());

        List<Instrument> results = instrumentService.searchInstruments(null);

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(instrumentRepository).searchInstruments(null);
    }

    /**
     * Test: searchInstruments with partial symbol match
     */
    @Test
    void searchInstruments_withPartialSymbolQuery_shouldReturnMatchingInstruments() {
        String query = "MS"; // partial match for "MSFT"
        List<Instrument> expectedResults = Arrays.asList(microsoftStock);
        when(instrumentRepository.searchInstruments(query)).thenReturn(expectedResults);

        List<Instrument> results = instrumentService.searchInstruments(query);

       assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("MSFT", results.get(0).getSymbol());
        verify(instrumentRepository).searchInstruments(query);
        verifyNoMoreInteractions(instrumentRepository);

    }

    /**
     * Test: searchInstruments repository is called exactly once
     */
    @Test
    void searchInstruments_shouldCallRepositoryMethodOnce() {
        String query = "AAPL";
        when(instrumentRepository.searchInstruments(query)).thenReturn(Arrays.asList(appleStock));

        instrumentService.searchInstruments(query);

       verify(instrumentRepository, times(1)).searchInstruments(query);
        verifyNoMoreInteractions(instrumentRepository);
    }

    /**
     * Test: getInstrumentById returns correct instrument
     */
    @Test
    void getInstrumentById_withValidId_shouldReturnInstrument() {
        Long instrumentId = 1L;
        when(instrumentRepository.findByInstrumentId(instrumentId)).thenReturn(Optional.of(appleStock));

        Optional<Instrument> result = instrumentService.getInstrumentById(instrumentId);

       assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().getSymbol());
        verify(instrumentRepository).findByInstrumentId(instrumentId);
        verifyNoMoreInteractions(instrumentRepository);
    }

    /**
     * Test: getInstrumentById returns empty Optional when instrument not found
     */
    @Test
    void getInstrumentById_withInvalidId_shouldReturnEmptyOptional() {
        Long instrumentId = 999L;
        when(instrumentRepository.findByInstrumentId(instrumentId)).thenReturn(Optional.empty());

        Optional<Instrument> result = instrumentService.getInstrumentById(instrumentId);

       assertTrue(result.isEmpty());
        verify(instrumentRepository).findByInstrumentId(instrumentId);
        verifyNoMoreInteractions(instrumentRepository);
    }

    /**
     * Test: getActiveInstruments returns active instruments
     */
    @Test
    void getActiveInstruments_withActiveFlag_shouldReturnActiveInstruments() {
        Boolean isActive = true;
        when(instrumentRepository.findByIsActive(isActive)).thenReturn(Optional.of(appleStock));

        Optional<Instrument> result = instrumentService.getActiveInstruments(isActive);

       assertTrue(result.isPresent());
        assertTrue(result.get().getIsActive());
        verify(instrumentRepository).findByIsActive(isActive);
        verifyNoMoreInteractions(instrumentRepository);
    }

    /**
     * Test: getActiveInstruments returns inactive instruments when requested
     */
    @Test
    void getActiveInstruments_withInactiveFlag_shouldReturnInactiveInstruments() {
        Boolean isActive = false;
        Instrument inactiveInstrument = createInstrument(4L, "DEAD", "Dead Stock", market, currency);
        inactiveInstrument.setIsActive(false);
        when(instrumentRepository.findByIsActive(isActive)).thenReturn(Optional.of(inactiveInstrument));

        Optional<Instrument> result = instrumentService.getActiveInstruments(isActive);

       assertTrue(result.isPresent());
        assertFalse(result.get().getIsActive());
        verify(instrumentRepository).findByIsActive(isActive);
        verifyNoMoreInteractions(instrumentRepository); 
    }

   
    private Instrument createInstrument(
            Long id,
            String symbol,
            String name,
            Market market,
            Currency currency) {
        Instrument instrument = new Instrument(
                market,
                symbol,
                name,
                InstrumentType.STOCK,
                currency);
        instrument.setInstrumentId(id);
        instrument.setIsActive(true);
        instrument.setIsTradeable(true);
        instrument.setCreatedAt(OffsetDateTime.now());
        instrument.setUpdatedAt(OffsetDateTime.now());
        return instrument;
    }
}
