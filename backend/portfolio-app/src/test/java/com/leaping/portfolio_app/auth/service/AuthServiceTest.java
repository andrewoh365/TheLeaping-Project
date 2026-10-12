package com.leaping.portfolio_app.auth.service;

import com.leaping.portfolio_app.auth.dto.RegisterRequest;
import com.leaping.portfolio_app.auth.dto.RegisterResponse;
import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.repository.CustomerRepository;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import com.leaping.portfolio_app.auth.util.JwtTokenProvider;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuthService.
 *
 * NOTE: AuthService.register() has many validation branches (duplicate
 * email, weak password, bad date format, etc.) that predate this session's
 * work. This file focuses specifically on the two things actually added
 * while building the portfolio feature:
 *   1. register() now also creates a Portfolio for the new customer.
 *   2. getUserIdByEmail(), used by PortfolioController to resolve the
 *      logged-in user's email (from the JWT) into a userId.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PortfolioRepository portfolioRepository;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRegisterRequest() {
        return new RegisterRequest(
                "newuser@example.com",
                "SecurePass123!",
                "SecurePass123!",
                "New",
                "User",
                "1990-05-15",
                "TAX-999"
        );
    }

    @Test
    void register_createsPortfolioLinkedToNewCustomer_withZeroBalance() {
        RegisterRequest request = validRegisterRequest();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(customerRepository.existsByTaxId(request.getTaxId())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");

        User savedUser = new User(request.getEmail(), "hashed-password",
                request.getFirstName(), request.getLastName());
        savedUser.setId(42L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(jwtTokenProvider.generateToken(savedUser.getEmail())).thenReturn("fake-token");
        when(jwtTokenProvider.generateRefreshToken(savedUser.getEmail())).thenReturn("fake-refresh-token");

        RegisterResponse response = authService.register(request);

        assertTrue(response.isSuccess());

        // Capture exactly what was handed to each repository's save() call,
        // so we can check the Portfolio really is linked to the right Customer.
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer savedCustomer = customerCaptor.getValue();

        ArgumentCaptor<Portfolio> portfolioCaptor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(portfolioCaptor.capture());
        Portfolio savedPortfolio = portfolioCaptor.getValue();

        // Same object reference - proves the Portfolio was built from THIS
        // customer, not a different/blank one.
        assertSame(savedCustomer, savedPortfolio.getCustomer());

        // BigDecimal.equals() also checks scale ("0.00" != 0 by .equals(),
        // even though they're the same number) - compareTo() checks value only,
        // which is what we actually care about here.
        assertEquals(0, BigDecimal.ZERO.compareTo(savedPortfolio.getCashBalanceUsd()));
    }

    @Test
    void register_doesNotCreatePortfolio_whenEmailAlreadyExists() {
        RegisterRequest request = validRegisterRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        RegisterResponse response = authService.register(request);

        assertTrue(!response.isSuccess());
        // Registration should fail before ever reaching the portfolio-creation step
        verify(portfolioRepository, never()).save(any(Portfolio.class));
    }

    @Test
    void getUserIdByEmail_returnsIdWhenUserExists() {
        User user = new User("someone@example.com", "hashed", "Some", "One");
        user.setId(7L);

        when(userRepository.findByEmail("someone@example.com")).thenReturn(Optional.of(user));

        Long result = authService.getUserIdByEmail("someone@example.com");

        assertEquals(7L, result);
    }

    @Test
    void getUserIdByEmail_throwsWhenUserNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> authService.getUserIdByEmail("ghost@example.com"));
    }
}
