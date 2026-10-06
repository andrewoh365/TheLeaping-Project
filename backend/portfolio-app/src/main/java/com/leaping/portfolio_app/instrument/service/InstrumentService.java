package com.leaping.portfolio_app.instrument.service;

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

    public Optional<Instrument> getInstrumentById(Long instrumentId) {
        return instrumentRepository.findByInstrumentId(instrumentId);   
    }

    public Optional<Instrument> getActiveInstruments(Boolean isActive) {
        return instrumentRepository.findByIsActive(isActive);
    }

    public List<Instrument> searchInstruments(String query) {
        return instrumentRepository.searchInstruments(query);
    }
}
