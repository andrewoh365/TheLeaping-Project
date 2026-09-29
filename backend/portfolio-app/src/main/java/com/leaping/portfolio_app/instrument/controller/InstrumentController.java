package com.leaping.portfolio_app.instrument.controller;

import com.leaping.portfolio_app.instrument.dto.InstrumentDTO;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.service.InstrumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/auth/instrument")
public class InstrumentController {
    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    public ResponseEntity<List<InstrumentDTO>> getAllInstruments() {
        List<Instrument> instruments = instrumentService.getAllInstruments();
        List<InstrumentDTO> dtos = instruments.stream()
            .map(InstrumentDTO::new)
            .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/search")
    public ResponseEntity<List<InstrumentDTO>> searchInstruments(@RequestParam String query) {
        List<Instrument> instruments = instrumentService.searchInstruments(query);
        List<InstrumentDTO> dtos = instruments.stream()
            .map(InstrumentDTO::new)
            .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstrumentDTO> getInstrumentById(@PathVariable Long id) {
        return instrumentService.getInstrumentById(id)
            .map(instrument -> ResponseEntity.ok(new InstrumentDTO(instrument)))
            .orElse(ResponseEntity.notFound().build());
    }
}
