package com.leaping.portfolio_app.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
import com.leaping.portfolio_app.market.entity.Price;
import com.leaping.portfolio_app.market.enums.PriceSourceType;
import com.leaping.portfolio_app.market.enums.TradabilityReason;
import com.leaping.portfolio_app.market.repository.CurrencyRepository;
import com.leaping.portfolio_app.market.repository.MarketRepository;
import com.leaping.portfolio_app.market.repository.PriceRepository;
import com.leaping.portfolio_app.market.service.MarketDataIngestionService;
import com.leaping.portfolio_app.market.service.MarketOverride;
import com.leaping.portfolio_app.market.service.PreTradeMarketService;
import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.portfolio.repository.PortfolioRepository;
import com.leaping.portfolio_app.trade.dto.PlaceOrderRequest;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.enums.OrderAction;
import com.leaping.portfolio_app.trade.enums.OrderStatus;
import com.leaping.portfolio_app.trade.enums.OrderType;
import com.leaping.portfolio_app.trade.enums.TimeInForce;
import com.leaping.portfolio_app.trade.exception.OrderNotExecutableException;
import com.leaping.portfolio_app.trade.exception.TradeRejectedException;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;
import com.leaping.portfolio_app.trade.service.OrderExecutionWorker;
import com.leaping.portfolio_app.trade.service.TradeOrderService;
import com.leaping.portfolio_app.trade.service.TradeService;

/**
 * The after-hours testing override may only change the market-hours check. These tests run the
 * real services against a database and compare every trading path with the switch on and off:
 * acceptance, execution, the tradability gate and DAY order expiry.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:overridetest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                "spring.datasource.driverClassName=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false",
                "spring.profiles.active=test",
                "jwt.secret=ThisIsATestOnlyJwtSecretKeyWithAtLeast64CharactersForHS512SigningAlgo",
                "trading.market-override.allowed=true",
                "trading.enforce-tradability=true"
        }
)
class MarketOverrideIntegrationTest {

    // Background work is replaced so nothing executes or expires test orders behind the tests' backs
    @MockitoBean
    private OrderExecutionWorker orderExecutionWorker;

    @MockitoBean
    private MarketDataIngestionService marketDataIngestionService;

    @Autowired private MarketOverride marketOverride;
    @Autowired private PreTradeMarketService preTradeMarketService;
    @Autowired private TradeOrderService tradeOrderService;
    @Autowired private TradeService tradeService;
    @Autowired private CurrencyRepository currencyRepository;
    @Autowired private MarketRepository marketRepository;
    @Autowired private InstrumentRepository instrumentRepository;
    @Autowired private PriceRepository priceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private TradeOrderRepository tradeOrderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private final String run = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    private Currency usd;
    private Market closedMarket;
    private Market openMarket;
    private Portfolio portfolio;
    private Long customerId;

    @BeforeEach
    void setUp() {
        // The schema here is generated from the entities; production's migration gives
        // audit_events.occurred_at a database default, which the generated schema lacks
        jdbcTemplate.execute("ALTER TABLE audit_events ALTER COLUMN occurred_at SET DEFAULT CURRENT_TIMESTAMP");
        marketOverride.setEnabled(false);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        usd = currency("USD", BigDecimal.ONE);
        // Closed now: today's session starts in 2 hours and closes in 3
        closedMarket = market("CLOSED-" + run, now.plusHours(2), now.plusHours(3));
        // Open now: the session started 2 hours ago and closes in 1
        openMarket = market("OPEN-" + run, now.minusHours(2), now.plusHours(1));

        User user = new User("override-" + run + "@example.com", "x", "Override", "Test");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus("ACTIVE");
        user = userRepository.save(user);
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setDateOfBirth(LocalDate.of(1990, 1, 1));
        customer.setTaxId("TAX-" + run);
        customer = customerRepository.save(customer);
        customerId = customer.getUserId();
        portfolio = new Portfolio(customer);
        portfolio.setCashBalanceUsd(new BigDecimal("100000.00"));
        portfolio = portfolioRepository.save(portfolio);
    }

    @AfterEach
    void tearDown() {
        marketOverride.setEnabled(false);
    }

    // ---- The tradability gate: the override changes market hours and nothing else ----

    @Test
    void overrideOnlyLiftsTheMarketHoursCheck() {
        Instrument healthy = instrument("OK", closedMarket, usd, true, true);
        price(healthy, usd, 0);
        Instrument inactive = instrument("INACT", closedMarket, usd, false, true);
        price(inactive, usd, 0);
        Instrument halted = instrument("HALT", closedMarket, usd, true, false);
        price(halted, usd, 0);
        Instrument stale = instrument("STALE", closedMarket, usd, true, true);
        price(stale, usd, 5 * 24 * 60);
        Instrument noRate = instrument("NORATE", closedMarket, currency("XR" + run.substring(0, 1), null), true, true);
        price(noRate, noRate.getPriceCurrency(), 0);
        Instrument noPrice = instrument("NOPRICE", closedMarket, usd, true, true);

        Map<Instrument, TradabilityReason> expectedWithOverride = new LinkedHashMap<>();
        expectedWithOverride.put(healthy, TradabilityReason.TRADABLE);
        expectedWithOverride.put(inactive, TradabilityReason.INSTRUMENT_INACTIVE);
        expectedWithOverride.put(halted, TradabilityReason.INSTRUMENT_NOT_TRADEABLE);
        expectedWithOverride.put(stale, TradabilityReason.PRICE_STALE);
        expectedWithOverride.put(noRate, TradabilityReason.PRICE_UNAVAILABLE);
        expectedWithOverride.put(noPrice, TradabilityReason.PRICE_UNAVAILABLE);

        // Off: everything in the closed market is blocked (instrument checks come first)
        assertEquals(TradabilityReason.MARKET_CLOSED, reason(healthy));
        assertEquals(TradabilityReason.INSTRUMENT_INACTIVE, reason(inactive));
        assertEquals(TradabilityReason.INSTRUMENT_NOT_TRADEABLE, reason(halted));
        assertEquals(TradabilityReason.MARKET_CLOSED, reason(stale));

        // On: only the healthy instrument becomes tradable; every other restriction still applies
        marketOverride.setEnabled(true);
        expectedWithOverride.forEach((instrument, expected) ->
            assertEquals(expected, reason(instrument), instrument.getSymbol()));
    }

    @Test
    void orderAcceptanceUnderOverrideStillRefusesNonHoursRestrictions() {
        Instrument healthy = instrument("OK", closedMarket, usd, true, true);
        price(healthy, usd, 0);
        Instrument stale = instrument("STALE", closedMarket, usd, true, true);
        price(stale, usd, 5 * 24 * 60);
        Instrument halted = instrument("HALT", closedMarket, usd, true, false);
        price(halted, usd, 0);
        Instrument noRate = instrument("NORATE", closedMarket, currency("XS" + run.substring(0, 1), null), true, true);
        price(noRate, noRate.getPriceCurrency(), 0);

        // Off: the closed market refuses even the healthy instrument
        assertThrows(IllegalArgumentException.class, () -> tradeOrderService.placeOrder(customerId, buy(healthy, OrderType.MARKET, null)));

        marketOverride.setEnabled(true);
        long ordersBefore = tradeOrderRepository.count();

        TradeOrder accepted = tradeOrderService.placeOrder(customerId, buy(healthy, OrderType.MARKET, null));
        assertEquals(OrderStatus.ACCEPTED, accepted.getOrderStatus());

        for (Instrument blocked : new Instrument[] { stale, halted, noRate }) {
            assertThrows(IllegalArgumentException.class,
                () -> tradeOrderService.placeOrder(customerId, buy(blocked, OrderType.MARKET, null)),
                blocked.getSymbol());
            assertThrows(IllegalArgumentException.class,
                () -> tradeOrderService.placeOrder(customerId, buy(blocked, OrderType.LIMIT, new BigDecimal("1000"))),
                blocked.getSymbol() + " limit");
        }
        // Only the healthy order was recorded
        assertEquals(ordersBefore + 1, tradeOrderRepository.count());
    }

    // ---- Execution uses the same gate ----

    @Test
    void executionUnderOverrideStillRefusesNonHoursRestrictions() {
        Instrument stale = instrument("STALE", closedMarket, usd, true, true);
        price(stale, usd, 5 * 24 * 60);
        Instrument halted = instrument("HALT", closedMarket, usd, true, false);
        price(halted, usd, 0);
        marketOverride.setEnabled(true);

        // Orders that were accepted earlier, before their instrument became untradable
        TradeOrder restingLimit = acceptedOrder(stale, OrderType.LIMIT, TimeInForce.GTC, new BigDecimal("1000"), OffsetDateTime.now());
        TradeOrder marketOrder = acceptedOrder(halted, OrderType.MARKET, TimeInForce.DAY, null, OffsetDateTime.now());

        // A limit order keeps resting, untouched
        assertThrows(OrderNotExecutableException.class, () -> tradeService.executeTrade(restingLimit.getOrderId()));
        assertEquals(OrderStatus.ACCEPTED, status(restingLimit));

        // A market order is rejected with the reason
        TradeRejectedException rejected = assertThrows(TradeRejectedException.class,
            () -> tradeService.executeTrade(marketOrder.getOrderId()));
        assertTrue(rejected.getMessage().contains("not tradeable"), rejected.getMessage());
        assertEquals(OrderStatus.REJECTED, status(marketOrder));

        assertTrue(tradeRepository.findByOrderOrderId(restingLimit.getOrderId()).isEmpty());
        assertTrue(tradeRepository.findByOrderOrderId(marketOrder.getOrderId()).isEmpty());
    }

    @Test
    void restingLimitOrderOnlyFillsAfterHoursWhileTheOverrideIsOn() {
        Instrument healthy = instrument("OK", closedMarket, usd, true, true);
        price(healthy, usd, 0);
        TradeOrder limit = acceptedOrder(healthy, OrderType.LIMIT, TimeInForce.GTC, new BigDecimal("1000"), OffsetDateTime.now());

        // Off: the market is closed, so the order keeps resting
        assertThrows(OrderNotExecutableException.class, () -> tradeService.executeTrade(limit.getOrderId()));
        assertEquals(OrderStatus.ACCEPTED, status(limit));

        // On: the intended effect, it fills
        marketOverride.setEnabled(true);
        tradeService.executeTrade(limit.getOrderId());
        assertEquals(OrderStatus.FILLED, status(limit));
    }

    // ---- DAY expiry depends on the order's own session, never on the override ----

    @Test
    void overrideNeverChangesWhetherADayOrderExpires() {
        Instrument inOpenMarket = instrument("OPENI", openMarket, usd, true, true);
        Instrument inClosedMarket = instrument("CLOSEDI", closedMarket, usd, true, true);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        record Scenario(String name, Instrument instrument, OffsetDateTime acceptedAt, boolean shouldExpire) { }
        Scenario[] scenarios = {
            // Accepted in today's session, which closes in an hour: still alive
            new Scenario("today's session, still open", inOpenMarket, now.minusHours(1), false),
            // Accepted in yesterday's session: its day is over even though today's session is open
            new Scenario("yesterday's session, market open now", inOpenMarket, now.minusHours(25), true),
            // Accepted after the close (only possible with the override): lasts until the next real close
            new Scenario("after the close, under the override", inClosedMarket, now.minusMinutes(10), false),
            // Accepted in a session that closed 21 hours ago: expired
            new Scenario("previous session, market closed now", inClosedMarket, now.minusHours(23), true),
        };

        for (Scenario scenario : scenarios) {
            TradeOrder whileOff = acceptedOrder(scenario.instrument(), OrderType.LIMIT, TimeInForce.DAY, new BigDecimal("0.01"), scenario.acceptedAt());
            TradeOrder whileOn = acceptedOrder(scenario.instrument(), OrderType.LIMIT, TimeInForce.DAY, new BigDecimal("0.01"), scenario.acceptedAt());

            marketOverride.setEnabled(false);
            boolean expiredWhileOff = tradeOrderService.expireDayOrder(whileOff.getOrderId());
            marketOverride.setEnabled(true);
            boolean expiredWhileOn = tradeOrderService.expireDayOrder(whileOn.getOrderId());
            marketOverride.setEnabled(false);

            assertEquals(scenario.shouldExpire(), expiredWhileOff, scenario.name() + " (override off)");
            assertEquals(scenario.shouldExpire(), expiredWhileOn, scenario.name() + " (override on)");
            assertEquals(scenario.shouldExpire() ? OrderStatus.EXPIRED : OrderStatus.ACCEPTED, status(whileOn), scenario.name());
        }
    }

    @Test
    void turningTheOverrideOffDoesNotExpireOrdersPlacedDuringIt() {
        Instrument healthy = instrument("OK", closedMarket, usd, true, true);
        price(healthy, usd, 0);

        marketOverride.setEnabled(true);
        TradeOrder placed = tradeOrderService.placeOrder(customerId, buy(healthy, OrderType.LIMIT, new BigDecimal("0.01")));
        marketOverride.setEnabled(false);

        assertFalse(tradeOrderService.expireDayOrder(placed.getOrderId()));
        assertEquals(OrderStatus.ACCEPTED, status(placed));
    }

    // ---- helpers ----

    /** Called inside a transaction, as placement, preview and execution all call it. */
    private TradabilityReason reason(Instrument instrument) {
        return new TransactionTemplate(transactionManager).execute(
            status -> preTradeMarketService.getTradability(instrument.getSymbol()).getReason());
    }

    private OrderStatus status(TradeOrder order) {
        return tradeOrderRepository.findById(order.getOrderId()).orElseThrow().getOrderStatus();
    }

    private PlaceOrderRequest buy(Instrument instrument, OrderType type, BigDecimal limitPrice) {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setInstrumentId(instrument.getInstrumentId());
        request.setOrderAction("BUY");
        request.setOrderType(type.name());
        request.setQuantity(BigDecimal.ONE);
        request.setLimitPrice(limitPrice);
        request.setTimeInForce("DAY");
        return request;
    }

    private TradeOrder acceptedOrder(Instrument instrument, OrderType type, TimeInForce timeInForce,
                                     BigDecimal limitPrice, OffsetDateTime acceptedAt) {
        TradeOrder order = new TradeOrder(portfolio, instrument, OrderAction.BUY, type, timeInForce, BigDecimal.ONE, limitPrice);
        order.setSubmittedAt(acceptedAt);
        order.setOrderStatus(OrderStatus.ACCEPTED);
        order.setAcceptedAt(acceptedAt);
        return tradeOrderRepository.save(order);
    }

    private Currency currency(String code, BigDecimal rateToUsd) {
        return currencyRepository.findById(code).orElseGet(() -> {
            Currency currency = new Currency();
            currency.setCurrencyCode(code);
            currency.setCurrencyName(code);
            currency.setCurrentExchangeRateToUsd(rateToUsd);
            return currencyRepository.save(currency);
        });
    }

    private Market market(String name, OffsetDateTime opens, OffsetDateTime closes) {
        Market market = new Market();
        market.setMarketName(name);
        market.setCountry("Test");
        market.setTimezone("UTC");
        market.setOpenTime(opens.toLocalTime());
        market.setCloseTime(closes.toLocalTime());
        market.setIsActive(true);
        return marketRepository.save(market);
    }

    private Instrument instrument(String symbol, Market market, Currency currency, boolean active, boolean tradeable) {
        Instrument instrument = new Instrument();
        instrument.setSymbol(symbol + run);
        instrument.setName(symbol + " test instrument");
        instrument.setInstrumentType(InstrumentType.STOCK);
        instrument.setMarket(market);
        instrument.setPriceCurrency(currency);
        instrument.setIsActive(active);
        instrument.setIsTradeable(tradeable);
        return instrumentRepository.save(instrument);
    }

    private void price(Instrument instrument, Currency currency, long minutesOld) {
        priceRepository.save(new Price(instrument, new BigDecimal("100.00"), currency,
            OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutesOld), PriceSourceType.MOCK, "test"));
    }
}
