package com.leaping.portfolio_app.config;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.leaping.portfolio_app.auth.model.Customer;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.model.UserRole;
import com.leaping.portfolio_app.auth.repository.CustomerRepository;
import com.leaping.portfolio_app.auth.repository.UserRepository;
import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.enums.InstrumentType;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.market.entity.Currency;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.market.repository.CurrencyRepository;
import com.leaping.portfolio_app.market.repository.MarketRepository;
import com.leaping.portfolio_app.portfolio.entity.Holding;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.HoldingRepository;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;

/**
 * DataSeeder: Populates development database with test data.
 * 
 * Runs only in 'dev' profile on application startup.
 * Creates test customers with mock holdings and cash balances.
 * 
 * Test Data Created:
 * - 2 Test Customers with portfolios
 * - Mock holdings (stocks, crypto, forex)
 * - Initial $10,000 USD cash balance each
 * - Realistic average costs for holdings
 */
@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PortfolioRepository portfolioRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketRepository marketRepository;
    private final CurrencyRepository currencyRepository;
    private final HoldingRepository holdingRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            UserRepository userRepository,
            CustomerRepository customerRepository,
            PortfolioRepository portfolioRepository,
            InstrumentRepository instrumentRepository,
            MarketRepository marketRepository,
            CurrencyRepository currencyRepository,
            HoldingRepository holdingRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.portfolioRepository = portfolioRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketRepository = marketRepository;
        this.currencyRepository = currencyRepository;
        this.holdingRepository = holdingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (shouldSeedData()) {
            System.out.println("🌱 Starting data seeder...");
            seedTestCustomers();
            System.out.println("✅ Data seeding completed successfully!");
        }
    }

    /**
     * Check if we should seed data (only if test users don't exist)
     */
    private boolean shouldSeedData() {
        return !userRepository.existsByEmail("john.trader@example.com");
    }

    /**
     * Create test customers with portfolios and mock holdings
     */
    private void seedTestCustomers() {
        // ===== Customer 1: John Trader =====
        User johnUser = createUser(
            "john.trader@example.com",
            "password123",
            "John",
            "Trader"
        );
        
        Customer johnCustomer = createCustomer(
            johnUser,
            "1990-05-15",
            "123-45-6789"
        );

        Portfolio johnPortfolio = createPortfolio(johnCustomer, new BigDecimal("10000.00"));

        // Add mock holdings for John
        addHolding(johnPortfolio, "AAPL", new BigDecimal("100"), new BigDecimal("195.50"));
        addHolding(johnPortfolio, "VOD", new BigDecimal("250"), new BigDecimal("8.75"));
        addHolding(johnPortfolio, "BTC-USD", new BigDecimal("0.5"), new BigDecimal("50000.00"));
        addHolding(johnPortfolio, "ETH-USD", new BigDecimal("2.5"), new BigDecimal("2000.00"));

        System.out.println("✓ Created test customer: john.trader@example.com");

        // ===== Customer 2: Sarah Investor =====
        User sarahUser = createUser(
            "sarah.investor@example.com",
            "password456",
            "Sarah",
            "Investor"
        );

        Customer sarahCustomer = createCustomer(
            sarahUser,
            "1988-08-22",
            "987-65-4321"
        );

        Portfolio sarahPortfolio = createPortfolio(sarahCustomer, new BigDecimal("10000.00"));

        // Add different mock holdings for Sarah
        addHolding(sarahPortfolio, "RELIANCE", new BigDecimal("30"), new BigDecimal("32.00"));
        addHolding(sarahPortfolio, "AAPL", new BigDecimal("20"), new BigDecimal("210.00"));
        addHolding(sarahPortfolio, "ETH-USD", new BigDecimal("1.2"), new BigDecimal("2100.00"));
        addHolding(sarahPortfolio, "EUR/USD", new BigDecimal("5000"), new BigDecimal("1.08"));
        addHolding(sarahPortfolio, "GBP/USD", new BigDecimal("2500"), new BigDecimal("1.25"));

        System.out.println("✓ Created test customer: sarah.investor@example.com");

        // ===== Customer 3: Demo User (minimal holdings) =====
        User demoUser = createUser(
            "demo@example.com",
            "password789",
            "Demo",
            "User"
        );

        Customer demoCustomer = createCustomer(
            demoUser,
            "2000-01-01",
            "111-11-1111"
        );

        Portfolio demoPortfolio = createPortfolio(demoCustomer, new BigDecimal("5000.00"));

        // Minimal holdings for demo user
        addHolding(demoPortfolio, "AAPL", new BigDecimal("10"), new BigDecimal("200.00"));
        addHolding(demoPortfolio, "BTC-USD", new BigDecimal("0.1"), new BigDecimal("45000.00"));

        System.out.println("✓ Created test customer: demo@example.com");
    }

    /**
     * Create a new User entity
     */
    private User createUser(String email, String password, String firstName, String lastName) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(UserRole.CUSTOMER);
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        return userRepository.save(user);
    }

    /**
     * Create a new Customer entity linked to a User
     */
    private Customer createCustomer(User user, String dateOfBirth, String taxId) {
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setDateOfBirth(java.time.LocalDate.parse(dateOfBirth));
        customer.setTaxId(taxId);

        return customerRepository.save(customer);
    }

    /**
     * Create a new Portfolio for a customer with initial cash balance
     */
    private Portfolio createPortfolio(Customer customer, BigDecimal cashBalance) {
        Portfolio portfolio = new Portfolio();
        portfolio.setCustomer(customer);
        portfolio.setCashBalanceUsd(cashBalance);
        portfolio.setCreatedAt(OffsetDateTime.now());
        portfolio.setUpdatedAt(OffsetDateTime.now());

        return portfolioRepository.save(portfolio);
    }

    /**
     * Add a holding to a portfolio.
     * Looks up or creates the instrument based on symbol.
     */
    private void addHolding(Portfolio portfolio, String symbol, BigDecimal quantity, BigDecimal averageCost) {
        // Get or create instrument
        Instrument instrument = getOrCreateInstrument(symbol);

        // Create holding
        Holding holding = new Holding();
        holding.setPortfolio(portfolio);
        holding.setInstrument(instrument);
        holding.setQuantity(quantity);
        holding.setAverageCostUsd(averageCost);
        holding.setUpdatedAt(OffsetDateTime.now());

        holdingRepository.save(holding);
    }

    /**
     * Get existing instrument by symbol, or create a new one if it doesn't exist.
     * For seeding, we create dummy instruments with minimal data.
     */
    private Instrument getOrCreateInstrument(String symbol) {
        Optional<Instrument> existing = instrumentRepository.findBySymbol(symbol);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new instrument
        // For seeding, we'll use a default market (US Stock Market)
        // In production, this would be properly associated with actual markets
        Market market = getOrCreateDefaultMarket();
        
        // Get or create USD currency
        Currency usdCurrency = getOrCreateUsdCurrency();

        Instrument instrument = new Instrument();
        instrument.setSymbol(symbol);
        instrument.setName(symbol + " Security");  // Placeholder name
        instrument.setMarket(market);
        instrument.setInstrumentType(getInstrumentType(symbol));
        instrument.setPriceCurrency(usdCurrency);
        instrument.setIsActive(true);
        instrument.setIsTradeable(true);
        instrument.setCreatedAt(OffsetDateTime.now());
        instrument.setUpdatedAt(OffsetDateTime.now());

        return instrumentRepository.save(instrument);
    }

    /**
     * Get or create USD currency
     */
    private Currency getOrCreateUsdCurrency() {
        Optional<Currency> existing = currencyRepository.findById("USD");
        if (existing.isPresent()) {
            return existing.get();
        }

        Currency usd = new Currency();
        usd.setCurrencyCode("USD");
        usd.setCurrencyName("United States Dollar");
        usd.setCurrencySymbol("$");
        usd.setCurrentExchangeRateToUsd(BigDecimal.ONE);
        usd.setExchangeRateUpdatedAt(OffsetDateTime.now());

        return currencyRepository.save(usd);
    }

    /**
     * Get or create a default market for seeded instruments
     */
    private Market getOrCreateDefaultMarket() {
        Optional<Market> existing = marketRepository.findByMarketName("NASDAQ");
        if (existing.isPresent()) {
            return existing.get();
        }

        Market market = new Market();
        market.setMarketName("NASDAQ");
        market.setCountry("United States");
        market.setTimezone("America/New_York");
        market.setOpenTime(java.time.LocalTime.of(9, 30));
        market.setCloseTime(java.time.LocalTime.of(16, 0));
        market.setIsActive(true);

        return marketRepository.save(market);
    }

    /**
     * Determine instrument type based on symbol naming conventions
     */
    private InstrumentType getInstrumentType(String symbol) {
        symbol = symbol.toUpperCase();

        // Crypto symbols (well-known abbreviations)
        if (symbol.matches("(BTC|ETH|ADA|SOL|XRP|DOGE|MATIC|BNB)")) {
            return InstrumentType.CRYPTO;
        }

        // Forex pairs (typically 6-letter pairs with USD)
        if (symbol.matches("[A-Z]{3}USD|USD[A-Z]{3}")) {
            return InstrumentType.FOREX;
        }

        // Default to STOCK for everything else
        return InstrumentType.STOCK;
    }
}
