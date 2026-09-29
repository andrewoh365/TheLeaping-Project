package com.leaping.portfolio_app.watchlist.repository;

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.watchlist.entity.Watchlist;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.leaping.portfolio_app.watchlist.service.WatchlistService;
import com.leaping.portfolio_app.watchlist.dto.CreateWatchlistRequest;
import com.leaping.portfolio_app.watchlist.dto.WatchlistResponse;
import com.leaping.portfolio_app.watchlist.dto.RenameWatchlistRequest;

import org.junit.jupiter.api.AfterEach;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
class WatchlistRepositoryTest {

    // Temporary database, separate from your existing leap-postgres container.
    @Container
    static final PostgreSQLContainer postgres
            = new PostgreSQLContainer("postgres:16")
                    .withDatabaseName("watchlist_test_db")
                    .withUsername("test_user")
                    .withPassword("test_password");

    // Direct BOTH Spring Data and Flyway to the temporary database.
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WatchlistService watchlistService;

    // Helper: Create a real user and customer using our V1 schema.
    private Long createCustomer(String label) {

        String uniqueId = UUID.randomUUID().toString();

        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (
                    first_name,
                    last_name,
                    email,
                    password_hash,
                    user_type,
                    status
                )
                VALUES (?, ?, ?, ?, 'CUSTOMER', 'ACTIVE')
                RETURNING user_id
                """,
                Long.class,
                label,
                "Tester",
                label + "-" + uniqueId + "@example.com",
                "test-password-hash"
        );

        jdbcTemplate.update("""
                INSERT INTO customers (
                    user_id,
                    date_of_birth,
                    tax_id
                )
                VALUES (?, DATE '1995-01-15', ?)
                """,
                userId,
                "TEST-" + uniqueId
        );

        return userId;
    }

    // Simulate an authenticated customer using their REAL database email.
    private void authenticateAs(Long customerId) {

        String email = jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE user_id = ?",
                String.class,
                customerId
        );

        var authentication = new UsernamePasswordAuthenticationToken(
                email,
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    // TEST 1: OWNER CAN RETRIEVE THEIR WATCHLIST
    @Test
    void findByWatchlistIdAndCustomerId_shouldFindOwnersWatchlist() {

        // Arrange: Create a customer and their watchlist.
        Long ownerId = createCustomer("owner");

        Customer owner = entityManager.getReference(
                Customer.class,
                ownerId
        );

        Watchlist saved = watchlistRepository.saveAndFlush(
                new Watchlist(owner, "Tech Stocks")
        );

        Long watchlistId = saved.getWatchlistId();

        // Clear cached entities so JPA performs a fresh lookup.
        entityManager.clear();

        // Act: Retrieve using the correct customer's ID.
        Optional<Watchlist> result
                = watchlistRepository.findByWatchlistIdAndCustomer_UserId(
                        watchlistId,
                        ownerId
                );

        // Assert
        assertTrue(result.isPresent());
        assertEquals(watchlistId, result.get().getWatchlistId());
        assertEquals("Tech Stocks", result.get().getWatchlistName());
        assertEquals(ownerId, result.get().getCustomer().getUserId());
    }

    // TEST 2: ANOTHER CUSTOMER CANNOT RETRIEVE THE WATCHLIST
    @Test
    void findByWatchlistIdAndCustomerId_shouldNotFindAnotherCustomersWatchlist() {

        // Arrange: Create two different customers.
        Long ownerId = createCustomer("owner");
        Long otherCustomerId = createCustomer("other");

        Customer owner = entityManager.getReference(
                Customer.class,
                ownerId
        );

        Watchlist saved = watchlistRepository.saveAndFlush(
                new Watchlist(owner, "Private Watchlist")
        );

        Long watchlistId = saved.getWatchlistId();

        entityManager.clear();

        // Act: Another customer attempts to retrieve the owner's watchlist.
        Optional<Watchlist> result
                = watchlistRepository.findByWatchlistIdAndCustomer_UserId(
                        watchlistId,
                        otherCustomerId
                );

        // Assert: The repository returns nothing.
        assertTrue(result.isEmpty());
    }

    @Test
    void findAllByCustomerId_shouldReturnOnlyOwnersWatchlists() {

        Long ownerId = createCustomer("owner");
        Long otherCustomerId = createCustomer("other");

        Customer owner = entityManager.getReference(Customer.class, ownerId);
        Customer other = entityManager.getReference(Customer.class, otherCustomerId);

        watchlistRepository.saveAndFlush(new Watchlist(owner, "Growth"));
        watchlistRepository.saveAndFlush(new Watchlist(owner, "Income"));
        watchlistRepository.saveAndFlush(new Watchlist(other, "Private"));

        entityManager.clear();

        var results
                = watchlistRepository.findAllByCustomer_UserIdOrderByCreatedAtDesc(ownerId);

        assertEquals(2, results.size());

        assertTrue(results.stream()
                .allMatch(watchlist -> ownerId.equals(watchlist.getCustomer().getUserId())));

        assertTrue(results.stream()
                .anyMatch(watchlist -> "Growth".equals(watchlist.getWatchlistName())));

        assertTrue(results.stream()
                .anyMatch(watchlist -> "Income".equals(watchlist.getWatchlistName())));
    }

    @Test
    void existsByCustomerIdAndName_shouldCheckOnlyThatCustomer() {

        Long ownerId = createCustomer("owner");
        Long otherCustomerId = createCustomer("other");

        Customer owner = entityManager.getReference(Customer.class, ownerId);

        watchlistRepository.saveAndFlush(
                new Watchlist(owner, "Tech Stocks")
        );

        entityManager.clear();

        // Owner already has this name.
        assertTrue(
                watchlistRepository.existsByCustomer_UserIdAndWatchlistName(
                        ownerId, "Tech Stocks"
                )
        );

        // Another customer does not have this name.
        assertFalse(
                watchlistRepository.existsByCustomer_UserIdAndWatchlistName(
                        otherCustomerId, "Tech Stocks"
                )
        );

        // Owner does not have this different name.
        assertFalse(
                watchlistRepository.existsByCustomer_UserIdAndWatchlistName(
                        ownerId, "Crypto"
                )
        );
    }

    @Test
    void existsByCustomerAndNameExcludingId_shouldIgnoreCurrentWatchlist() {

        Long ownerId = createCustomer("owner");

        Customer owner = entityManager.getReference(Customer.class, ownerId);

        Watchlist growth = watchlistRepository.saveAndFlush(
                new Watchlist(owner, "Growth")
        );

        Watchlist income = watchlistRepository.saveAndFlush(
                new Watchlist(owner, "Income")
        );

        entityManager.clear();

        // The current watchlist should not count as its own duplicate.
        assertFalse(
                watchlistRepository.existsByCustomer_UserIdAndWatchlistNameAndWatchlistIdNot(
                        ownerId,
                        "Growth",
                        growth.getWatchlistId()
                )
        );

        // A DIFFERENT watchlist already uses this name.
        assertTrue(
                watchlistRepository.existsByCustomer_UserIdAndWatchlistNameAndWatchlistIdNot(
                        ownerId,
                        "Growth",
                        income.getWatchlistId()
                )
        );
    }

    @Test
    void saveWatchlist_shouldRejectDuplicateNameForSameCustomer() {

        Long ownerId = createCustomer("owner");

        Customer owner = entityManager.getReference(Customer.class, ownerId);

        // First watchlist: valid.
        watchlistRepository.saveAndFlush(
                new Watchlist(owner, "Tech Stocks")
        );

        // Second watchlist with the same owner and name: rejected by PostgreSQL.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> watchlistRepository.saveAndFlush(
                        new Watchlist(owner, "Tech Stocks")
                )
        );
    }

    @Test
    void service_shouldCreateWatchlistForAuthenticatedCustomer() {

        // Arrange: Create a real customer in our temporary database.
        Long customerId = createCustomer("service-owner");

        authenticateAs(customerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);

        when(request.getWatchlistName()).thenReturn("Tech Stocks");

        // Act: Call the REAL service.
        WatchlistResponse response = watchlistService.createWatchlist(request);

        // Assert: The service returned the saved watchlist.
        assertNotNull(response.getWatchlistId());
        assertEquals("Tech Stocks", response.getWatchlistName());

        // Verify PostgreSQL saved it under the authenticated customer.
        entityManager.flush();
        entityManager.clear();

        Optional<Watchlist> saved
                = watchlistRepository.findByWatchlistIdAndCustomer_UserId(
                        response.getWatchlistId(),
                        customerId
                );

        assertTrue(saved.isPresent());
        assertEquals("Tech Stocks", saved.get().getWatchlistName());
    }

    @Test
    void service_shouldRejectAccessToAnotherCustomersWatchlist() {

        // Arrange: Create two real customers.
        Long ownerId = createCustomer("service-owner");
        Long otherCustomerId = createCustomer("service-other");

        // Owner creates a watchlist.
        authenticateAs(ownerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);

        when(request.getWatchlistName()).thenReturn("Private Watchlist");

        WatchlistResponse created = watchlistService.createWatchlist(request);

        Long watchlistId = created.getWatchlistId();

        // Switch authentication to the OTHER customer.
        authenticateAs(otherCustomerId);

        // Act: Other customer attempts to retrieve the owner's watchlist.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.getWatchlist(watchlistId)
        );

        // Assert: Service hides the watchlist from the non-owner.
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // Confirm the watchlist still exists in PostgreSQL.
        assertTrue(watchlistRepository.findById(watchlistId).isPresent());
    }

    @Test
    void service_shouldPreventAnotherCustomerFromRenamingWatchlist() {

        Long ownerId = createCustomer("rename-owner");
        Long otherCustomerId = createCustomer("rename-other");

        // Owner creates a watchlist.
        authenticateAs(ownerId);

        CreateWatchlistRequest createRequest = mock(CreateWatchlistRequest.class);
        when(createRequest.getWatchlistName()).thenReturn("Original Name");

        WatchlistResponse created = watchlistService.createWatchlist(createRequest);
        Long watchlistId = created.getWatchlistId();

        // Another customer attempts to rename it.
        authenticateAs(otherCustomerId);

        RenameWatchlistRequest renameRequest = mock(RenameWatchlistRequest.class);
        when(renameRequest.getWatchlistName()).thenReturn("Hacked Name");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.renameWatchlist(watchlistId, renameRequest)
        );

        // Unauthorized customer receives 404.
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // The original name remains unchanged.
        Watchlist saved = watchlistRepository.findById(watchlistId).orElseThrow();
        assertEquals("Original Name", saved.getWatchlistName());
    }

    @Test
    void service_shouldPreventAnotherCustomerFromDeletingWatchlist() {

        Long ownerId = createCustomer("delete-owner");
        Long otherCustomerId = createCustomer("delete-other");

        // Owner creates a watchlist.
        authenticateAs(ownerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);
        when(request.getWatchlistName()).thenReturn("Private Watchlist");

        WatchlistResponse created = watchlistService.createWatchlist(request);
        Long watchlistId = created.getWatchlistId();

        // Another customer attempts to delete it.
        authenticateAs(otherCustomerId);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.deleteWatchlist(watchlistId)
        );

        // Unauthorized customer receives 404.
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // The watchlist still exists in PostgreSQL.
        assertTrue(watchlistRepository.findById(watchlistId).isPresent());
    }

    @Test
    void service_shouldPreventAnotherCustomerFromViewingWatchlistInstruments() {

        Long ownerId = createCustomer("view-owner");
        Long otherCustomerId = createCustomer("view-other");

        authenticateAs(ownerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);
        when(request.getWatchlistName()).thenReturn("Private Watchlist");

        Long watchlistId = watchlistService.createWatchlist(request)
                .getWatchlistId();

        authenticateAs(otherCustomerId);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.getWatchlistInstruments(watchlistId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void service_shouldPreventAnotherCustomerFromAddingInstrument() {

        Long ownerId = createCustomer("add-owner");
        Long otherCustomerId = createCustomer("add-other");

        authenticateAs(ownerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);
        when(request.getWatchlistName()).thenReturn("Private Watchlist");

        Long watchlistId = watchlistService.createWatchlist(request)
                .getWatchlistId();

        authenticateAs(otherCustomerId);

        // Ownership must be checked before looking up the instrument.
        Long instrumentId = 999999L;

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.addInstrument(watchlistId, instrumentId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // No instruments were added to the owner's watchlist.
        Integer savedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchlist_instruments WHERE watchlist_id = ?",
                Integer.class,
                watchlistId
        );

        assertEquals(0, savedCount);
    }

    @Test
    void service_shouldPreventAnotherCustomerFromRemovingInstrument() {

        Long ownerId = createCustomer("remove-owner");
        Long otherCustomerId = createCustomer("remove-other");

        authenticateAs(ownerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);
        when(request.getWatchlistName()).thenReturn("Private Watchlist");

        Long watchlistId = watchlistService.createWatchlist(request)
                .getWatchlistId();

        // Switch to another customer.
        authenticateAs(otherCustomerId);

        // Ownership should be checked before instrument membership.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.removeInstrument(watchlistId, 999999L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // The owner's watchlist still exists.
        assertTrue(watchlistRepository.findById(watchlistId).isPresent());
    }

    @Test
    void service_shouldAllowOwnerToRenameWatchlist() {

        Long ownerId = createCustomer("rename-success-owner");

        authenticateAs(ownerId);

        CreateWatchlistRequest createRequest = mock(CreateWatchlistRequest.class);
        when(createRequest.getWatchlistName()).thenReturn("Original Name");

        Long watchlistId = watchlistService.createWatchlist(createRequest)
                .getWatchlistId();

        RenameWatchlistRequest renameRequest = mock(RenameWatchlistRequest.class);
        when(renameRequest.getWatchlistName()).thenReturn("Updated Name");

        // Call the real service.
        WatchlistResponse response
                = watchlistService.renameWatchlist(watchlistId, renameRequest);

        assertEquals("Updated Name", response.getWatchlistName());

        // Verify the change was saved in PostgreSQL.
        entityManager.flush();
        entityManager.clear();

        Watchlist saved = watchlistRepository.findById(watchlistId).orElseThrow();

        assertEquals("Updated Name", saved.getWatchlistName());
        assertEquals(ownerId, saved.getCustomer().getUserId());
    }

    @Test
    void service_shouldAllowOwnerToDeleteWatchlist() {

        Long ownerId = createCustomer("delete-success-owner");

        authenticateAs(ownerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);
        when(request.getWatchlistName()).thenReturn("To Be Deleted");

        Long watchlistId = watchlistService.createWatchlist(request)
                .getWatchlistId();

        // Confirm the watchlist exists before deletion.
        assertTrue(watchlistRepository.existsById(watchlistId));

        // Owner deletes their watchlist using the real service.
        watchlistService.deleteWatchlist(watchlistId);

        // Flush the deletion to PostgreSQL and clear cached entities.
        entityManager.flush();
        entityManager.clear();

        // Confirm it no longer exists.
        assertFalse(watchlistRepository.existsById(watchlistId));
    }

    @Test
    void service_shouldListOnlyAuthenticatedCustomersWatchlists() {

        Long ownerId = createCustomer("list-owner");
        Long otherCustomerId = createCustomer("list-other");

        // Create two watchlists for the owner.
        authenticateAs(ownerId);

        CreateWatchlistRequest growth = mock(CreateWatchlistRequest.class);
        when(growth.getWatchlistName()).thenReturn("Growth");
        watchlistService.createWatchlist(growth);

        CreateWatchlistRequest income = mock(CreateWatchlistRequest.class);
        when(income.getWatchlistName()).thenReturn("Income");
        watchlistService.createWatchlist(income);

        // Create a private watchlist for another customer.
        authenticateAs(otherCustomerId);

        CreateWatchlistRequest privateRequest = mock(CreateWatchlistRequest.class);
        when(privateRequest.getWatchlistName()).thenReturn("Private");
        watchlistService.createWatchlist(privateRequest);

        // Switch back to the owner.
        authenticateAs(ownerId);

        var results = watchlistService.getWatchlists();

        // Owner sees exactly their two watchlists.
        assertEquals(2, results.size());

        assertTrue(results.stream()
                .anyMatch(watchlist
                        -> "Growth".equals(watchlist.getWatchlistName())));

        assertTrue(results.stream()
                .anyMatch(watchlist
                        -> "Income".equals(watchlist.getWatchlistName())));

        // Other customer's watchlist must not appear.
        assertFalse(results.stream()
                .anyMatch(watchlist
                        -> "Private".equals(watchlist.getWatchlistName())));
    }

    @Test
    void service_shouldAllowOwnerToAddAndViewInstrument() {

        Long ownerId = createCustomer("instrument-add-owner");
        authenticateAs(ownerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);
        when(request.getWatchlistName()).thenReturn("Tech Stocks");

        Long watchlistId = watchlistService.createWatchlist(request)
                .getWatchlistId();

        // Use AAPL from the V2 seed data.
        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT instrument_id FROM instruments WHERE symbol = 'AAPL'",
                Long.class
        );

        // Add the instrument through the real service.
        var added = watchlistService.addInstrument(watchlistId, instrumentId);

        assertEquals(instrumentId, added.getInstrumentId());
        assertEquals("AAPL", added.getSymbol());

        // Flush and clear so the next lookup reads the database.
        entityManager.flush();
        entityManager.clear();

        // Retrieve the saved instruments through the real service.
        var results = watchlistService.getWatchlistInstruments(watchlistId);

        assertEquals(1, results.size());
        assertEquals(instrumentId, results.get(0).getInstrumentId());
        assertEquals("AAPL", results.get(0).getSymbol());

        // Confirm the composite-key relationship exists in PostgreSQL.
        Integer count = jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*)
            FROM watchlist_instruments
            WHERE watchlist_id = ? AND instrument_id = ?
            """,
                Integer.class,
                watchlistId,
                instrumentId
        );

        assertEquals(1, count);
    }

    @Test
    void service_shouldAllowOwnerToRemoveInstrument() {

        Long ownerId = createCustomer("instrument-remove-owner");
        authenticateAs(ownerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);
        when(request.getWatchlistName()).thenReturn("Tech Stocks");

        Long watchlistId = watchlistService.createWatchlist(request)
                .getWatchlistId();

        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT instrument_id FROM instruments WHERE symbol = 'AAPL'",
                Long.class
        );

        // First, save the instrument.
        watchlistService.addInstrument(watchlistId, instrumentId);

        entityManager.flush();
        entityManager.clear();

        // Confirm the relationship exists before removal.
        assertEquals(1, watchlistService
                .getWatchlistInstruments(watchlistId)
                .size());

        // Remove it through the real service.
        watchlistService.removeInstrument(watchlistId, instrumentId);

        entityManager.flush();
        entityManager.clear();

        // Verify the relationship was deleted from PostgreSQL.
        Integer count = jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*)
            FROM watchlist_instruments
            WHERE watchlist_id = ? AND instrument_id = ?
            """,
                Integer.class,
                watchlistId,
                instrumentId
        );

        assertEquals(0, count);

        // The watchlist itself should still exist.
        assertTrue(watchlistRepository.existsById(watchlistId));

        // The saved-instrument list should now be empty.
        assertTrue(watchlistService
                .getWatchlistInstruments(watchlistId)
                .isEmpty());
    }

    @Test
    void service_shouldRejectAddingDuplicateInstrument() {

        Long ownerId = createCustomer("duplicate-instrument-owner");
        authenticateAs(ownerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);
        when(request.getWatchlistName()).thenReturn("Tech Stocks");

        Long watchlistId = watchlistService.createWatchlist(request)
                .getWatchlistId();

        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT instrument_id FROM instruments WHERE symbol = 'AAPL'",
                Long.class
        );

        // First addition succeeds.
        watchlistService.addInstrument(watchlistId, instrumentId);

        entityManager.flush();
        entityManager.clear();

        // Adding the same instrument again should fail.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> watchlistService.addInstrument(watchlistId, instrumentId)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        // PostgreSQL should still contain exactly one relationship.
        Integer count = jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*)
            FROM watchlist_instruments
            WHERE watchlist_id = ? AND instrument_id = ?
            """,
                Integer.class,
                watchlistId,
                instrumentId
        );

        assertEquals(1, count);
    }

    @Test
    void service_shouldDeleteWatchlistWithSavedInstruments() {

        Long ownerId = createCustomer("delete-with-instrument-owner");
        authenticateAs(ownerId);

        CreateWatchlistRequest request = mock(CreateWatchlistRequest.class);
        when(request.getWatchlistName()).thenReturn("Tech Stocks");

        Long watchlistId = watchlistService.createWatchlist(request)
                .getWatchlistId();

        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT instrument_id FROM instruments WHERE symbol = 'AAPL'",
                Long.class
        );

        // Save an instrument before deleting the watchlist.
        watchlistService.addInstrument(watchlistId, instrumentId);

        entityManager.flush();
        entityManager.clear();

        // Confirm the relationship exists.
        Integer beforeDelete = jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*)
            FROM watchlist_instruments
            WHERE watchlist_id = ?
            """,
                Integer.class,
                watchlistId
        );

        assertEquals(1, beforeDelete);

        // Delete through the real service.
        watchlistService.deleteWatchlist(watchlistId);

        entityManager.flush();
        entityManager.clear();

        // Both the watchlist and its saved relationships should be gone.
        assertFalse(watchlistRepository.existsById(watchlistId));

        Integer afterDelete = jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*)
            FROM watchlist_instruments
            WHERE watchlist_id = ?
            """,
                Integer.class,
                watchlistId
        );

        assertEquals(0, afterDelete);
    }
}
