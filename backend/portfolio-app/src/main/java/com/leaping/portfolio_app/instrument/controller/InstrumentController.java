package com.leaping.portfolio_app.instrument.controller;

import com.leaping.portfolio_app.instrument.dto.InstrumentDTO;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.service.InstrumentService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
@CrossOrigin(origins = "*")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(
        InstrumentService instrumentService
    ) {
        this.instrumentService = instrumentService;
    }

    /**
     * Get all available instruments.
     *
     * GET /api/instruments
     */
    @GetMapping
    public ResponseEntity<List<InstrumentDTO>> getAllInstruments() {

        List<InstrumentDTO> instruments =
            instrumentService
                .getAllInstruments()
                .stream()
                .map(InstrumentDTO::new)
                .toList();

        return ResponseEntity.ok(instruments);
    }

    /**
     * Get a single instrument by ID.
     *
     * GET /api/instruments/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<InstrumentDTO> getInstrumentById(
        @PathVariable Long id
    ) {
        return instrumentService
            .getInstrumentById(id)
            .map(InstrumentDTO::new)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Search instruments by symbol or name.
     *
     * GET /api/instruments/search?query=AAPL
     */
    @GetMapping("/search")
    public ResponseEntity<List<InstrumentDTO>> searchInstruments(
        @RequestParam String query
    ) {

        List<InstrumentDTO> instruments =
            instrumentService
                .searchInstruments(query)
                .stream()
                .map(InstrumentDTO::new)
                .toList();

        return ResponseEntity.ok(instruments);
    }
}