package com.leaping.portfolio_app.service;

import com.leaping.portfolio_app.entity.instrument;
import com.leaping.portfolio_app.repository.InstrumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InstrumentService {
    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    public List<instrument> getAllInstruments() {
        return instrumentRepository.findAll();
    }

    public Optional<instrument> getInstrumentById(Long id) {
        return instrumentRepository.findById(id);
    }

    public Optional<instrument> getActiveInstruments(Boolean isActive) {
        return instrumentRepository.findByIsActive(isActive);
    }
}
