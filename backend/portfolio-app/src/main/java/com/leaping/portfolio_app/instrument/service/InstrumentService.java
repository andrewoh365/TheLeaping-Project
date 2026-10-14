package com.leaping.portfolio_app.instrument.service;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.market.entity.Price;
import com.leaping.portfolio_app.market.repository.PriceRepository;
import com.leaping.portfolio_app.instrument.dto.InstrumentDetail;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InstrumentService {
    private final InstrumentRepository instrumentRepository;
    private final PriceRepository priceRepository;

    public InstrumentService(
            InstrumentRepository instrumentRepository,
            PriceRepository priceRepository
    ) {
        this.instrumentRepository = instrumentRepository;
        this.priceRepository = priceRepository;
    }

    public List<Instrument> getAllInstruments() {
        return instrumentRepository.findAll();
    }

    public Optional<Instrument> getInstrumentById(Long instrumentId) {
        return instrumentRepository.findByInstrumentId(instrumentId);   
    }

    public Optional<InstrumentDetail> getInstrumentDetailById(Long instrumentId) {
        return instrumentRepository.findByInstrumentId(instrumentId)
                .map(instrument -> {
                    Price latestPrice = priceRepository
                            .findFirstByInstrumentSymbolIgnoreCaseOrderByPriceTimestampDesc(instrument.getSymbol())
                            .orElse(null);
                    return new InstrumentDetail(instrument, instrument.getMarket(), latestPrice);
                });
    }

    public Optional<Instrument> getActiveInstruments(Boolean isActive) {
        return instrumentRepository.findByIsActive(isActive);
    }

    public List<Instrument> searchInstruments(String query) {
        return instrumentRepository.searchInstruments(query);
    }
}
