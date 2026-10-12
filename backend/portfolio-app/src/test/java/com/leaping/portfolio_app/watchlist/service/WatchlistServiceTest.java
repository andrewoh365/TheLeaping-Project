
package com.leaping.portfolio_app.watchlist.service;

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.model.UserRole;
import com.leaping.portfolio_app.auth.repository.CustomerRepository;
import com.leaping.portfolio_app.auth.repository.UserRepository;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;

import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import com.leaping.portfolio_app.watchlist.dto.CreateWatchlistRequest;
import com.leaping.portfolio_app.watchlist.dto.WatchlistResponse;
import com.leaping.portfolio_app.watchlist.dto.RenameWatchlistRequest;
import com.leaping.portfolio_app.watchlist.dto.WatchlistInstrumentResponse;
import com.leaping.portfolio_app.watchlist.entity.Watchlist;
import com.leaping.portfolio_app.watchlist.entity.WatchlistInstrument;
import com.leaping.portfolio_app.watchlist.entity.WatchlistInstrumentId;
import com.leaping.portfolio_app.watchlist.repository.WatchlistRepository;
import com.leaping.portfolio_app.watchlist.repository.WatchlistInstrumentRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private WatchlistInstrumentRepository watchlistInstrumentRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @Mock
    private User user;

    @Mock
    private Customer customer;

    @InjectMocks
    private WatchlistService watchlistService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createWatchlist_shouldCreateWatchlistSuccessfully() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        CreateWatchlistRequest request =
                new CreateWatchlistRequest("Tech Stocks");

        // Simulate the database assigning an ID
        when(watchlistRepository.save(any(Watchlist.class)))
                .thenAnswer(invocation -> {
                    Watchlist watchlist = invocation.getArgument(0);
                    watchlist.setWatchlistId(10L);
                    return watchlist;
                });

        // Act: Call the service
        WatchlistResponse response =
                watchlistService.createWatchlist(request);

        // Assert: Verify the result
        assertNotNull(response);
        assertEquals(10L, response.getWatchlistId());
        assertEquals("Tech Stocks", response.getWatchlistName());

        // Verify the correct customer and name were saved
        verify(watchlistRepository).save(argThat(watchlist ->
                watchlist.getCustomer() == customer
                && watchlist.getWatchlistName().equals("Tech Stocks")
        ));
    }

    @Test
    void createWatchlist_shouldRejectDuplicateName() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        CreateWatchlistRequest request =
                new CreateWatchlistRequest("Tech Stocks");

        // Simulate an existing watchlist with the same name
        when(watchlistRepository
                .existsByCustomer_UserIdAndWatchlistName(
                        1L,
                        "Tech Stocks"
                ))
                .thenReturn(true);

        // Act: Attempt to create the duplicate watchlist
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.createWatchlist(request)
        );

        // Assert: Verify the correct error
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        // Verify that no new watchlist was saved
        verify(watchlistRepository, never()).save(any(Watchlist.class));
    }
    
    @Test
    void getWatchlist_shouldRejectAccessToAnotherCustomersWatchlist() {

        // Arrange: Set up authenticated Customer A
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("customerA@example.com");

        when(userRepository.findByEmail("customerA@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Watchlist 10 does not belong to Customer A
        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.empty());

        // Act: Customer A attempts to retrieve watchlist 10
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.getWatchlist(10L)
        );

        // Assert: Access is denied with 404
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // Verify the repository checked BOTH the watchlist ID and customer ID
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify the service did not retrieve the watchlist by ID alone
        verify(watchlistRepository, never()).findById(10L);
    }

    @Test
    void getWatchlist_shouldReturnOwnedWatchlist() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Create an existing watchlist belonging to this customer
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        OffsetDateTime now = OffsetDateTime.now();

        watchlist.setCreatedAt(now);
        watchlist.setUpdatedAt(now);

        // Simulate finding the customer's watchlist
        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // Act: Retrieve the watchlist
        WatchlistResponse response =
                watchlistService.getWatchlist(10L);

        // Assert: Verify the returned information
        assertNotNull(response);
        assertEquals(10L, response.getWatchlistId());
        assertEquals("Tech Stocks", response.getWatchlistName());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify no unrestricted lookup was performed
        verify(watchlistRepository, never()).findById(10L);
    }

    @Test
    void getWatchlists_shouldReturnAllCustomerWatchlists() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Create two watchlists belonging to Customer 1
        Watchlist watchlist1 = new Watchlist(customer, "Tech Stocks");
        watchlist1.setWatchlistId(10L);

        Watchlist watchlist2 = new Watchlist(customer, "Crypto Favorites");
        watchlist2.setWatchlistId(11L);

        // Simulate database returning both watchlists
        when(watchlistRepository
                .findAllByCustomer_UserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(watchlist1, watchlist2));

        // Act: Retrieve all customer watchlists
        List<WatchlistResponse> responses =
                watchlistService.getWatchlists();

        // Assert: Verify both watchlists were returned
        assertNotNull(responses);
        assertEquals(2, responses.size());

        assertEquals(10L, responses.get(0).getWatchlistId());
        assertEquals("Tech Stocks", responses.get(0).getWatchlistName());

        assertEquals(11L, responses.get(1).getWatchlistId());
        assertEquals("Crypto Favorites", responses.get(1).getWatchlistName());

        // Verify the query was restricted to Customer 1
        verify(watchlistRepository)
                .findAllByCustomer_UserIdOrderByCreatedAtDesc(1L);
    }
    
    @Test
    void renameWatchlist_shouldRenameOwnedWatchlist() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // New requested name
        RenameWatchlistRequest request =
                new RenameWatchlistRequest("Favorite Stocks");

        // Simulate saving the updated watchlist
        when(watchlistRepository.save(any(Watchlist.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: Rename the watchlist
        WatchlistResponse response =
                watchlistService.renameWatchlist(10L, request);

        // Assert: Verify the updated information
        assertNotNull(response);
        assertEquals(10L, response.getWatchlistId());
        assertEquals("Favorite Stocks", response.getWatchlistName());

        // Verify the entity itself was updated
        assertEquals("Favorite Stocks", watchlist.getWatchlistName());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify duplicate-name validation occurred
        verify(watchlistRepository)
                .existsByCustomer_UserIdAndWatchlistNameAndWatchlistIdNot(
                        1L,
                        "Favorite Stocks",
                        10L
                );

        // Verify the updated watchlist was saved
        verify(watchlistRepository).save(watchlist);
    }
    
    @Test
    void renameWatchlist_shouldRejectDuplicateName() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist belonging to Customer 1
        Watchlist watchlist = new Watchlist(customer, "Crypto Favorites");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // Customer tries to rename it to an existing name
        RenameWatchlistRequest request =
                new RenameWatchlistRequest("Tech Stocks");

        // Simulate another watchlist already using this name
        when(watchlistRepository
                .existsByCustomer_UserIdAndWatchlistNameAndWatchlistIdNot(
                        1L,
                        "Tech Stocks",
                        10L
                ))
                .thenReturn(true);

        // Act: Attempt to rename the watchlist
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.renameWatchlist(10L, request)
        );

        // Assert: Verify the correct error
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        // Verify the original name was not changed
        assertEquals("Crypto Favorites", watchlist.getWatchlistName());

        // Verify the repository did not save the duplicate name
        verify(watchlistRepository, never()).save(any(Watchlist.class));
    }
        
    @Test
    void deleteWatchlist_shouldDeleteOwnedWatchlistAndItsInstruments() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist belonging to Customer 1
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // Act: Delete the watchlist
        watchlistService.deleteWatchlist(10L);

        // Assert: Verify the watchlist belongs to the customer
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify deletion happens in the correct order
        InOrder inOrder = inOrder(
                watchlistInstrumentRepository,
                watchlistRepository
        );

        // First: Delete instrument relationships
        inOrder.verify(watchlistInstrumentRepository)
                .deleteAllByWatchlist_WatchlistId(10L);

        // Second: Flush the deletions
        inOrder.verify(watchlistInstrumentRepository).flush();

        // Third: Delete the actual watchlist
        inOrder.verify(watchlistRepository).delete(watchlist);
    }

    @Test
    void deleteWatchlist_shouldRejectAccessToAnotherCustomersWatchlist() {

        // Arrange: Set up authenticated Customer A
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("customerA@example.com");

        when(userRepository.findByEmail("customerA@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Watchlist 10 does not belong to Customer A
        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.empty());

        // Act: Customer A attempts to delete watchlist 10
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.deleteWatchlist(10L)
        );

        // Assert: Verify the correct error
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify no instrument relationships were deleted
        verifyNoInteractions(watchlistInstrumentRepository);

        // Verify the watchlist itself was not deleted
        verify(watchlistRepository, never())
                .delete(any(Watchlist.class));
    }

    @Test
    void getWatchlistInstruments_shouldReturnSavedInstruments() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist belonging to Customer 1
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));
        
        // Mock an instrument saved inside the watchlist
        Instrument instrument = mock(Instrument.class);

        WatchlistInstrument savedInstrument =
                mock(WatchlistInstrument.class);

        when(savedInstrument.getInstrument()).thenReturn(instrument);

        when(instrument.getInstrumentId()).thenReturn(25L);
        when(instrument.getSymbol()).thenReturn("AAPL");
        when(instrument.getName()).thenReturn("Apple Inc.");
        when(instrument.getInstrumentType()).thenReturn(InstrumentType.STOCK);

        // Simulate the repository returning the saved instrument
        when(watchlistInstrumentRepository
                .findAllByWatchlist_WatchlistId(10L))
                .thenReturn(List.of(savedInstrument));

        // Act: Retrieve the instruments
        List<WatchlistInstrumentResponse> responses =
                watchlistService.getWatchlistInstruments(10L);

        // Assert: Verify the returned information
        assertNotNull(responses);
        assertEquals(1, responses.size());

        WatchlistInstrumentResponse response = responses.get(0);

        assertEquals(25L, response.getInstrumentId());
        assertEquals("AAPL", response.getSymbol());
        assertEquals("Apple Inc.", response.getName());
        assertEquals(InstrumentType.STOCK, response.getInstrumentType());

        // Verify customer ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify instruments were retrieved from the correct watchlist
        verify(watchlistInstrumentRepository)
                .findAllByWatchlist_WatchlistId(10L);
    }
    
    @Test
    void getWatchlistInstruments_shouldRejectAnotherCustomersWatchlist() {

        // Arrange: Set up authenticated Customer A
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("customerA@example.com");

        when(userRepository.findByEmail("customerA@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Watchlist 10 does not belong to Customer A
        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.empty());

        // Act: Attempt to retrieve another customer's instruments
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.getWatchlistInstruments(10L)
        );

        // Assert: Verify the correct error
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify no instruments were retrieved
        verifyNoInteractions(watchlistInstrumentRepository);
    }
    
    @Test
    void addInstrument_shouldAddInstrumentSuccessfully() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist belonging to Customer 1
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // Mock an existing, active instrument
        Instrument instrument = mock(Instrument.class);

        when(instrumentRepository.findById(25L))
                .thenReturn(Optional.of(instrument));

        when(instrument.getIsActive()).thenReturn(true);
        when(instrument.getInstrumentId()).thenReturn(25L);
        when(instrument.getSymbol()).thenReturn("AAPL");
        when(instrument.getName()).thenReturn("Apple Inc.");
        when(instrument.getInstrumentType()).thenReturn(InstrumentType.STOCK);

        // Simulate saving the relationship
        when(watchlistInstrumentRepository.save(any(WatchlistInstrument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: Add Apple stock to watchlist 10
        WatchlistInstrumentResponse response =
                watchlistService.addInstrument(10L, 25L);

        // Assert: Verify the returned instrument
        assertNotNull(response);
        assertEquals(25L, response.getInstrumentId());
        assertEquals("AAPL", response.getSymbol());
        assertEquals("Apple Inc.", response.getName());
        assertEquals(InstrumentType.STOCK, response.getInstrumentType());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify the instrument exists
        verify(instrumentRepository).findById(25L);

        // Verify the relationship does not already exist
        WatchlistInstrumentId id =
                new WatchlistInstrumentId(10L, 25L);

        verify(watchlistInstrumentRepository).existsById(id);

        // Verify the correct relationship was saved
        verify(watchlistInstrumentRepository).save(argThat(saved ->
                saved.getWatchlist() == watchlist
                && saved.getInstrument() == instrument
                && saved.getId().equals(id)
        ));
    }
    
    @Test
    void addInstrument_shouldRejectDuplicateInstrument() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist belonging to Customer 1
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // Existing, active instrument
        Instrument instrument = mock(Instrument.class);

        when(instrumentRepository.findById(25L))
                .thenReturn(Optional.of(instrument));

        when(instrument.getIsActive()).thenReturn(true);

        // Instrument 25 already belongs to watchlist 10
        WatchlistInstrumentId id =
                new WatchlistInstrumentId(10L, 25L);

        when(watchlistInstrumentRepository.existsById(id))
                .thenReturn(true);

        // Act: Attempt to add the same instrument again
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.addInstrument(10L, 25L)
        );

        // Assert: Verify the correct error
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify the duplicate was detected
        verify(watchlistInstrumentRepository).existsById(id);

        // Verify no duplicate relationship was saved
        verify(watchlistInstrumentRepository, never())
                .save(any(WatchlistInstrument.class));
    }
    
    @Test
    void addInstrument_shouldRejectNonexistentInstrument() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist belonging to Customer 1
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // Instrument 999 does not exist
        when(instrumentRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act: Attempt to add the nonexistent instrument
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.addInstrument(10L, 999L)
        );

        // Assert: Verify the correct error
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify the instrument lookup occurred
        verify(instrumentRepository).findById(999L);

        // Verify no instrument relationship was saved
        verify(watchlistInstrumentRepository, never())
                .save(any(WatchlistInstrument.class));
    }
    
    @Test
    void addInstrument_shouldRejectInactiveInstrument() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist belonging to Customer 1
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // Instrument exists but is inactive
        Instrument instrument = mock(Instrument.class);

        when(instrumentRepository.findById(25L))
                .thenReturn(Optional.of(instrument));

        when(instrument.getIsActive()).thenReturn(false);

        // Act: Attempt to add the inactive instrument
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.addInstrument(10L, 25L)
        );

        // Assert: Verify the correct error
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        // Verify customer ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify the instrument was retrieved
        verify(instrumentRepository).findById(25L);

        // Verify inactive status was checked
        verify(instrument).getIsActive();

        // Verify no instrument relationship was created
        verifyNoInteractions(watchlistInstrumentRepository);
    }

    @Test
    void removeInstrument_shouldRemoveInstrumentSuccessfully() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist belonging to Customer 1
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // Instrument 25 is already saved in watchlist 10
        WatchlistInstrumentId id =
                new WatchlistInstrumentId(10L, 25L);

        when(watchlistInstrumentRepository.existsById(id))
                .thenReturn(true);

        // Act: Remove instrument 25 from watchlist 10
        watchlistService.removeInstrument(10L, 25L);

        // Assert: Verify customer ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify the instrument relationship exists
        verify(watchlistInstrumentRepository).existsById(id);

        // Verify the relationship was deleted
        verify(watchlistInstrumentRepository).deleteById(id);

        // Verify the actual instrument was not deleted
        verifyNoInteractions(instrumentRepository);
    }

    @Test
    void removeInstrument_shouldRejectNonexistentRelationship() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist belonging to Customer 1
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // Instrument 25 is NOT saved in watchlist 10
        WatchlistInstrumentId id =
                new WatchlistInstrumentId(10L, 25L);

        when(watchlistInstrumentRepository.existsById(id))
                .thenReturn(false);

        // Act: Attempt to remove the nonexistent relationship
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.removeInstrument(10L, 25L)
        );

        // Assert: Verify the correct error
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // Verify customer ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify the relationship was checked
        verify(watchlistInstrumentRepository).existsById(id);

        // Verify nothing was deleted
        verify(watchlistInstrumentRepository, never())
                .deleteById(any(WatchlistInstrumentId.class));

        // Verify the actual instrument was not accessed
        verifyNoInteractions(instrumentRepository);
    }
    
    @Test
    void removeInstrument_shouldRejectAnotherCustomersWatchlist() {

        // Arrange: Set up authenticated Customer A
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("customerA@example.com");

        when(userRepository.findByEmail("customerA@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Watchlist 10 does not belong to Customer A
        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.empty());

        // Act: Attempt to remove instrument 25
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.removeInstrument(10L, 25L)
        );

        // Assert: Verify the correct error
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify no instrument relationships were accessed or deleted
        verifyNoInteractions(watchlistInstrumentRepository);

        // Verify the actual instrument was not accessed
        verifyNoInteractions(instrumentRepository);
    }
    
    @Test
    void createWatchlist_shouldRejectUnauthenticatedUser() {

        // Arrange: Simulate no authenticated user
        SecurityContextHolder.clearContext();

        CreateWatchlistRequest request =
                new CreateWatchlistRequest("Tech Stocks");

        // Act: Attempt to create a watchlist
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.createWatchlist(request)
        );

        // Assert: Verify 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        // Verify no database operations were attempted
        verifyNoInteractions(
                userRepository,
                customerRepository,
                watchlistRepository,
                watchlistInstrumentRepository,
                instrumentRepository
        );
    }
    
    @Test
    void createWatchlist_shouldRejectInactiveCustomer() {

        // Arrange: Set up an authenticated user
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        // The user's account is inactive
        when(user.isActive()).thenReturn(false);

        CreateWatchlistRequest request =
                new CreateWatchlistRequest("Tech Stocks");

        // Act: Attempt to create a watchlist
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.createWatchlist(request)
        );

        // Assert: Verify 403 Forbidden
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        // Verify the user's active status was checked
        verify(user).isActive();

        // Verify no customer or watchlist operations occurred
        verifyNoInteractions(
                customerRepository,
                watchlistRepository,
                watchlistInstrumentRepository,
                instrumentRepository
        );
    }
    
    @Test
    void createWatchlist_shouldRejectNonCustomerRole() {

        // Arrange: Set up an authenticated user
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("analyst@example.com");

        when(userRepository.findByEmail("analyst@example.com"))
                .thenReturn(Optional.of(user));

        // User is active but has the ANALYST role
        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.ANALYST);

        CreateWatchlistRequest request =
                new CreateWatchlistRequest("Tech Stocks");

        // Act: Analyst attempts to create a watchlist
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.createWatchlist(request)
        );

        // Assert: Verify 403 Forbidden
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        // Verify the user's role was checked
        verify(user).getRole();

        // Verify no customer or watchlist operations occurred
        verifyNoInteractions(
                customerRepository,
                watchlistRepository,
                watchlistInstrumentRepository,
                instrumentRepository
        );
    }
    
    @Test
    void createWatchlist_shouldRejectBlankName() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        // Watchlist name contains only spaces
        CreateWatchlistRequest request =
                new CreateWatchlistRequest("   ");

        // Act: Attempt to create the watchlist
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.createWatchlist(request)
        );

        // Assert: Verify 400 Bad Request
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        // Verify no watchlist was saved or queried
        verifyNoInteractions(
                watchlistRepository,
                watchlistInstrumentRepository,
                instrumentRepository
        );
    }
    
    @Test
    void createWatchlist_shouldRejectNameOver100Characters() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        // Create a name containing 101 characters
        String longName = "A".repeat(101);

        CreateWatchlistRequest request =
                new CreateWatchlistRequest(longName);

        // Act: Attempt to create the watchlist
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.createWatchlist(request)
        );

        // Assert: Verify 400 Bad Request
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        // Verify no watchlist database operations occurred
        verifyNoInteractions(
                watchlistRepository,
                watchlistInstrumentRepository,
                instrumentRepository
        );
    }
    
    @Test
    void createWatchlist_shouldTrimWhitespace() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Name contains unnecessary spaces
        CreateWatchlistRequest request =
                new CreateWatchlistRequest("  Tech Stocks  ");

        // Simulate saving the watchlist
        when(watchlistRepository.save(any(Watchlist.class)))
                .thenAnswer(invocation -> {
                    Watchlist watchlist = invocation.getArgument(0);
                    watchlist.setWatchlistId(10L);
                    return watchlist;
                });

        // Act: Create the watchlist
        WatchlistResponse response =
                watchlistService.createWatchlist(request);

        // Assert: Verify the returned name is trimmed
        assertNotNull(response);
        assertEquals(10L, response.getWatchlistId());
        assertEquals("Tech Stocks", response.getWatchlistName());

        // Verify duplicate checking uses the trimmed name
        verify(watchlistRepository)
                .existsByCustomer_UserIdAndWatchlistName(
                        1L,
                        "Tech Stocks"
                );

        // Verify the trimmed name was saved
        verify(watchlistRepository).save(argThat(watchlist ->
                watchlist.getWatchlistName().equals("Tech Stocks")
        ));
    }
    
    @Test
    void createWatchlist_shouldAcceptExactly100Characters() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Create a name containing exactly 100 characters
        String name = "A".repeat(100);

        CreateWatchlistRequest request =
                new CreateWatchlistRequest(name);

        // Simulate saving the watchlist
        when(watchlistRepository.save(any(Watchlist.class)))
                .thenAnswer(invocation -> {
                    Watchlist watchlist = invocation.getArgument(0);
                    watchlist.setWatchlistId(10L);
                    return watchlist;
                });

        // Act: Create the watchlist
        WatchlistResponse response =
                watchlistService.createWatchlist(request);

        // Assert: Verify creation succeeded
        assertNotNull(response);
        assertEquals(10L, response.getWatchlistId());
        assertEquals(name, response.getWatchlistName());
        assertEquals(100, response.getWatchlistName().length());

        // Verify the correct name was checked for duplicates
        verify(watchlistRepository)
                .existsByCustomer_UserIdAndWatchlistName(1L, name);

        // Verify the watchlist was saved
        verify(watchlistRepository).save(argThat(watchlist ->
                watchlist.getWatchlistName().equals(name)
        ));
    }
    
    @Test
    void addInstrument_shouldRejectAnotherCustomersWatchlist() {

        // Arrange: Set up authenticated Customer A
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("customerA@example.com");

        when(userRepository.findByEmail("customerA@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Watchlist 10 does not belong to Customer A
        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.empty());

        // Act: Attempt to add instrument 25
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.addInstrument(10L, 25L)
        );

        // Assert: Verify 404 Not Found
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify the instrument was never accessed
        verifyNoInteractions(instrumentRepository);

        // Verify no watchlist-instrument operations occurred
        verifyNoInteractions(watchlistInstrumentRepository);
    }
    
    @Test
    void renameWatchlist_shouldAllowSameNameWithoutUpdating() {

        // Arrange: Set up authenticated customer
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Existing watchlist
        Watchlist watchlist = new Watchlist(customer, "Tech Stocks");
        watchlist.setWatchlistId(10L);

        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.of(watchlist));

        // Customer submits the same name
        RenameWatchlistRequest request =
                new RenameWatchlistRequest("Tech Stocks");

        // Act: Rename using the existing name
        WatchlistResponse response =
                watchlistService.renameWatchlist(10L, request);

        // Assert: Verify the response
        assertNotNull(response);
        assertEquals(10L, response.getWatchlistId());
        assertEquals("Tech Stocks", response.getWatchlistName());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify no unnecessary duplicate-name query occurred
        verify(watchlistRepository, never())
                .existsByCustomer_UserIdAndWatchlistNameAndWatchlistIdNot(
                        anyLong(),
                        anyString(),
                        anyLong()
                );

        // Verify the unchanged watchlist was not saved again
        verify(watchlistRepository, never())
                .save(any(Watchlist.class));
    }
    
    @Test
    void renameWatchlist_shouldRejectAnotherCustomersWatchlist() {

        // Arrange: Set up authenticated Customer A
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("customerA@example.com");

        when(userRepository.findByEmail("customerA@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
        when(user.getId()).thenReturn(1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customer.getUserId()).thenReturn(1L);

        // Watchlist 10 does not belong to Customer A
        when(watchlistRepository
                .findByWatchlistIdAndCustomer_UserId(10L, 1L))
                .thenReturn(Optional.empty());

        RenameWatchlistRequest request =
                new RenameWatchlistRequest("Favorite Stocks");

        // Act: Attempt to rename another customer's watchlist
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.renameWatchlist(10L, request)
        );

        // Assert: Verify 404 Not Found
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // Verify ownership was checked
        verify(watchlistRepository)
                .findByWatchlistIdAndCustomer_UserId(10L, 1L);

        // Verify duplicate-name validation was never reached
        verify(watchlistRepository, never())
                .existsByCustomer_UserIdAndWatchlistNameAndWatchlistIdNot(
                        anyLong(),
                        anyString(),
                        anyLong()
                );

        // Verify no watchlist was saved
        verify(watchlistRepository, never())
                .save(any(Watchlist.class));
    }
}
