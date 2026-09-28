package com.leaping.portfolio_app.service;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InstrumentService {
    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    public List<Instrument> getAllInstruments() {
        return instrumentRepository.findAll();
    }

    public Optional<Instrument> getInstrumentById(Long id) {
        return instrumentRepository.findById(id);
    }

    public Optional<Instrument> getActiveInstruments(Boolean isActive) {
        return instrumentRepository.findByIsActive(isActive);
    }
}
