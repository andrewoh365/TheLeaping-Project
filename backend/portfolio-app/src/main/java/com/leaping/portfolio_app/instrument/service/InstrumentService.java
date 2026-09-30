package com.leaping.portfolio_app.service;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.repository.InstrumentRepository;
import org.springframework.stereotype.Service;
import com.leaping.portfolio_app.instrument.entity.Instrument;

import java.util.List;
import java.util.Optional;

@Service
public class InstrumentService {
    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    public List<Instrument> getAllInstruments() {
        return instrumentRepository.findAllWithCurrency();
    }

    public Optional<Instrument> getInstrumentById(Long id) {
        return instrumentRepository.findByIdWithCurrency(id);
    }

    public Optional<Instrument> getActiveInstruments(Boolean isActive) {
        return instrumentRepository.findByIsActive(isActive);
    }

    public List<Instrument> searchInstruments(String query) {
        return instrumentRepository.searchInstruments(query);
    }
}
