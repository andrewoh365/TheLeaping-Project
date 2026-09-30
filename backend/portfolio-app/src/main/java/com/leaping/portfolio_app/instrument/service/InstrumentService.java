package com.leaping.portfolio_app.service;
<<<<<<< HEAD
=======

import com.leaping.portfolio_app.instrument.entity.Instrument;
>>>>>>> b71bbdb95a93ad930c47a96f85470589bfdad665
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
<<<<<<< HEAD
        return instrumentRepository.findAllWithCurrency();
    }

    public Optional<Instrument> getInstrumentById(Long id) {
        return instrumentRepository.findByIdWithCurrency(id);
=======
        return instrumentRepository.findAll();
    }

    public Optional<Instrument> getInstrumentById(Long id) {
        return instrumentRepository.findById(id);
>>>>>>> b71bbdb95a93ad930c47a96f85470589bfdad665
    }

    public Optional<Instrument> getActiveInstruments(Boolean isActive) {
        return instrumentRepository.findByIsActive(isActive);
    }

    public List<Instrument> searchInstruments(String query) {
        return instrumentRepository.searchInstruments(query);
    }
}
