
package com.leaping.portfolio_app.watchlist.service;

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.model.UserRole;
import com.leaping.portfolio_app.auth.repository.CustomerRepository;
import com.leaping.portfolio_app.auth.repository.UserRepository;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;

import com.leaping.portfolio_app.watchlist.dto.CreateWatchlistRequest;
import com.leaping.portfolio_app.watchlist.dto.RenameWatchlistRequest;
import com.leaping.portfolio_app.watchlist.dto.WatchlistResponse;
import com.leaping.portfolio_app.watchlist.dto.WatchlistInstrumentResponse;

import com.leaping.portfolio_app.watchlist.entity.Watchlist;
import com.leaping.portfolio_app.watchlist.entity.WatchlistInstrument;
import com.leaping.portfolio_app.watchlist.entity.WatchlistInstrumentId;

import com.leaping.portfolio_app.watchlist.repository.WatchlistRepository;
import com.leaping.portfolio_app.watchlist.repository.WatchlistInstrumentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistInstrumentRepository watchlistInstrumentRepository;
    private final InstrumentRepository instrumentRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            WatchlistInstrumentRepository watchlistInstrumentRepository,
            InstrumentRepository instrumentRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository
    ) {
        this.watchlistRepository = watchlistRepository;
        this.watchlistInstrumentRepository = watchlistInstrumentRepository;
        this.instrumentRepository = instrumentRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    // CREATE WATCHLIST

    @Transactional
    public WatchlistResponse createWatchlist(CreateWatchlistRequest request) {

        Customer customer = getAuthenticatedCustomer();
        String name = validateWatchlistName(request.getWatchlistName());

        boolean exists = watchlistRepository
                .existsByCustomer_UserIdAndWatchlistName(
                        customer.getUserId(),
                        name
                );

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A watchlist with this name already exists"
            );
        }

        Watchlist watchlist = new Watchlist(customer, name);

        Watchlist savedWatchlist = watchlistRepository.save(watchlist);

        return toWatchlistResponse(savedWatchlist);
    }

    // GET ALL WATCHLISTS

    @Transactional(readOnly = true)
    public List<WatchlistResponse> getWatchlists() {

        Customer customer = getAuthenticatedCustomer();

        return watchlistRepository
                .findAllByCustomer_UserIdOrderByCreatedAtDesc(
                        customer.getUserId()
                )
                .stream()
                .map(this::toWatchlistResponse)
                .toList();
    }

    // GET ONE WATCHLIST

    @Transactional(readOnly = true)
    public WatchlistResponse getWatchlist(Long watchlistId) {

        Customer customer = getAuthenticatedCustomer();

        Watchlist watchlist = findOwnedWatchlist(
                watchlistId,
                customer.getUserId()
        );

        return toWatchlistResponse(watchlist);
    }

    // RENAME WATCHLIST

    @Transactional
    public WatchlistResponse renameWatchlist(
            Long watchlistId,
            RenameWatchlistRequest request
    ) {

        Customer customer = getAuthenticatedCustomer();

        Watchlist watchlist = findOwnedWatchlist(
                watchlistId,
                customer.getUserId()
        );

        String name = validateWatchlistName(request.getWatchlistName());

        if (watchlist.getWatchlistName().equals(name)) {
            return toWatchlistResponse(watchlist);
        }

        boolean exists = watchlistRepository
                .existsByCustomer_UserIdAndWatchlistNameAndWatchlistIdNot(
                        customer.getUserId(),
                        name,
                        watchlistId
                );

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A watchlist with this name already exists"
            );
        }

        watchlist.setWatchlistName(name);

        Watchlist savedWatchlist = watchlistRepository.save(watchlist);

        return toWatchlistResponse(savedWatchlist);
    }

    // DELETE WATCHLIST

    @Transactional
    public void deleteWatchlist(Long watchlistId) {

        Customer customer = getAuthenticatedCustomer();

        Watchlist watchlist = findOwnedWatchlist(
                watchlistId,
                customer.getUserId()
        );

        watchlistInstrumentRepository
                .deleteAllByWatchlist_WatchlistId(watchlistId);

        watchlistInstrumentRepository.flush();

        watchlistRepository.delete(watchlist);
    }

    // GET INSTRUMENTS IN A WATCHLIST

    @Transactional(readOnly = true)
    public List<WatchlistInstrumentResponse> getWatchlistInstruments(
            Long watchlistId
    ) {

        Customer customer = getAuthenticatedCustomer();

        findOwnedWatchlist(watchlistId, customer.getUserId());

        return watchlistInstrumentRepository
                .findAllByWatchlist_WatchlistId(watchlistId)
                .stream()
                .map(this::toInstrumentResponse)
                .toList();
    }

    // ADD INSTRUMENT TO WATCHLIST

    @Transactional
    public WatchlistInstrumentResponse addInstrument(
            Long watchlistId,
            Long instrumentId
    ) {

        Customer customer = getAuthenticatedCustomer();

        Watchlist watchlist = findOwnedWatchlist(
                watchlistId,
                customer.getUserId()
        );

        Instrument instrument = instrumentRepository
                .findById(instrumentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Instrument not found"
                ));

        if (!Boolean.TRUE.equals(instrument.getIsActive())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Instrument is inactive"
            );
        }

        WatchlistInstrumentId id = new WatchlistInstrumentId(
                watchlistId,
                instrumentId
        );

        if (watchlistInstrumentRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Instrument is already in this watchlist"
            );
        }

        WatchlistInstrument watchlistInstrument =
                new WatchlistInstrument(watchlist, instrument);

        WatchlistInstrument savedInstrument =
                watchlistInstrumentRepository.save(watchlistInstrument);

        return toInstrumentResponse(savedInstrument);
    }

    // REMOVE INSTRUMENT FROM WATCHLIST

    @Transactional
    public void removeInstrument(Long watchlistId, Long instrumentId) {

        Customer customer = getAuthenticatedCustomer();

        findOwnedWatchlist(watchlistId, customer.getUserId());

        WatchlistInstrumentId id = new WatchlistInstrumentId(
                watchlistId,
                instrumentId
        );

        if (!watchlistInstrumentRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Instrument is not in this watchlist"
            );
        }

        watchlistInstrumentRepository.deleteById(id);
    }

    // GET THE AUTHENTICATED CUSTOMER

    private Customer getAuthenticatedCustomer() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        if (!user.isActive() || user.getRole() != UserRole.CUSTOMER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Customer access required"
            );
        }

        return customerRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Customer record not found"
                ));
    }

    // FIND WATCHLIST AND VERIFY OWNERSHIP

    private Watchlist findOwnedWatchlist(
            Long watchlistId,
            Long customerId
    ) {

        return watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(
                        watchlistId,
                        customerId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Watchlist not found"
                ));
    }

    // VALIDATE WATCHLIST NAME

    private String validateWatchlistName(String name) {

        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Watchlist name is required"
            );
        }

        String trimmedName = name.trim();

        if (trimmedName.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Watchlist name must be 100 characters or less"
            );
        }

        return trimmedName;
    }

    // CONVERT WATCHLIST ENTITY TO RESPONSE DTO

    private WatchlistResponse toWatchlistResponse(Watchlist watchlist) {

        return new WatchlistResponse(
                watchlist.getWatchlistId(),
                watchlist.getWatchlistName(),
                watchlist.getCreatedAt(),
                watchlist.getUpdatedAt()
        );
    }

    // CONVERT INSTRUMENT ENTITY TO RESPONSE DTO

    private WatchlistInstrumentResponse toInstrumentResponse(
            WatchlistInstrument watchlistInstrument
    ) {

        Instrument instrument = watchlistInstrument.getInstrument();

        return new WatchlistInstrumentResponse(
                instrument.getInstrumentId(),
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getInstrumentType()
        );
    }
}
