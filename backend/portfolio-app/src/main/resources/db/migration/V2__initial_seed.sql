BEGIN;

-- =========================================================
-- CURRENCIES
--
-- Rates below are MOCK development values, not live rates.
-- The market-data layer can update these later.
-- =========================================================

INSERT INTO currencies (
    currency_code,
    currency_name,
    currency_symbol,
    current_exchange_rate_to_usd,
    exchange_rate_updated_at
)
VALUES
    (
        'USD',
        'US Dollar',
        '$',
        1.0000000000,
        CURRENT_TIMESTAMP
    ),
    (
        'GBP',
        'British Pound',
        '£',
        1.3500000000,
        CURRENT_TIMESTAMP
    ),
    (
        'INR',
        'Indian Rupee',
        '₹',
        0.0120000000,
        CURRENT_TIMESTAMP
    ),
    (
        'EUR',
        'Euro',
        '€',
        1.1700000000,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (currency_code) DO NOTHING;


-- =========================================================
-- MARKETS
-- =========================================================

INSERT INTO markets (
    market_name,
    country,
    timezone,
    open_time,
    close_time,
    is_active
)
VALUES
    (
        'NASDAQ',
        'United States',
        'America/New_York',
        '09:30',
        '16:00',
        TRUE
    ),
    (
        'NYSE',
        'United States',
        'America/New_York',
        '09:30',
        '16:00',
        TRUE
    ),
    (
        'LSE',
        'United Kingdom',
        'Europe/London',
        '08:00',
        '16:30',
        TRUE
    ),
    (
        'NSE',
        'India',
        'Asia/Kolkata',
        '09:15',
        '15:30',
        TRUE
    ),
    (
        'FOREX',
        NULL,
        'UTC',
        NULL,
        NULL,
        TRUE
    ),
    (
        'CRYPTO',
        NULL,
        'UTC',
        NULL,
        NULL,
        TRUE
    )
ON CONFLICT (market_name) DO NOTHING;


-- =========================================================
-- INSTRUMENTS
--
-- Includes examples covering:
--   US equity
--   UK equity
--   Indian equity
--   Crypto
--   Forex
-- =========================================================

INSERT INTO instruments (
    market_id,
    symbol,
    name,
    instrument_type,
    price_currency_code,
    is_tradeable,
    is_active
)
VALUES
    (
        (
            SELECT market_id
            FROM markets
            WHERE market_name = 'NASDAQ'
        ),
        'AAPL',
        'Apple Inc.',
        'STOCK',
        'USD',
        TRUE,
        TRUE
    ),
    (
        (
            SELECT market_id
            FROM markets
            WHERE market_name = 'LSE'
        ),
        'VOD',
        'Vodafone Group',
        'STOCK',
        'GBP',
        TRUE,
        TRUE
    ),
    (
        (
            SELECT market_id
            FROM markets
            WHERE market_name = 'NSE'
        ),
        'RELIANCE',
        'Reliance Industries',
        'STOCK',
        'INR',
        TRUE,
        TRUE
    ),
    (
        (
            SELECT market_id
            FROM markets
            WHERE market_name = 'CRYPTO'
        ),
        'BTC-USD',
        'Bitcoin',
        'CRYPTO',
        'USD',
        TRUE,
        TRUE
    ),
    (
        (
            SELECT market_id
            FROM markets
            WHERE market_name = 'CRYPTO'
        ),
        'ETH-USD',
        'Ethereum',
        'CRYPTO',
        'USD',
        TRUE,
        TRUE
    ),
    (
        (
            SELECT market_id
            FROM markets
            WHERE market_name = 'FOREX'
        ),
        'EUR/USD',
        'Euro / US Dollar',
        'FOREX',
        'USD',
        TRUE,
        TRUE
    ),
    (
        (
            SELECT market_id
            FROM markets
            WHERE market_name = 'FOREX'
        ),
        'GBP/USD',
        'British Pound / US Dollar',
        'FOREX',
        'USD',
        TRUE,
        TRUE
    )
ON CONFLICT (market_id, symbol) DO NOTHING;


-- =========================================================
-- STOCK DETAILS
-- =========================================================

INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    i.instrument_id,
    'Technology',
    'Consumer Electronics',
    'United States'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'AAPL'
  AND m.market_name = 'NASDAQ'
ON CONFLICT (instrument_id) DO NOTHING;


INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    i.instrument_id,
    'Communication Services',
    'Telecommunications',
    'United Kingdom'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'VOD'
  AND m.market_name = 'LSE'
ON CONFLICT (instrument_id) DO NOTHING;


INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    i.instrument_id,
    'Energy',
    'Diversified',
    'India'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'RELIANCE'
  AND m.market_name = 'NSE'
ON CONFLICT (instrument_id) DO NOTHING;


-- =========================================================
-- CRYPTO DETAILS
-- =========================================================

INSERT INTO cryptos (
    instrument_id,
    blockchain
)
SELECT
    i.instrument_id,
    'Bitcoin'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'BTC-USD'
  AND m.market_name = 'CRYPTO'
ON CONFLICT (instrument_id) DO NOTHING;


INSERT INTO cryptos (
    instrument_id,
    blockchain
)
SELECT
    i.instrument_id,
    'Ethereum'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'ETH-USD'
  AND m.market_name = 'CRYPTO'
ON CONFLICT (instrument_id) DO NOTHING;


-- =========================================================
-- FOREX DETAILS
-- =========================================================

INSERT INTO forexes (
    instrument_id,
    base_currency_code,
    quote_currency_code
)
SELECT
    i.instrument_id,
    'EUR',
    'USD'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'EUR/USD'
  AND m.market_name = 'FOREX'
ON CONFLICT DO NOTHING;


INSERT INTO forexes (
    instrument_id,
    base_currency_code,
    quote_currency_code
)
SELECT
    i.instrument_id,
    'GBP',
    'USD'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'GBP/USD'
  AND m.market_name = 'FOREX'
ON CONFLICT DO NOTHING;


-- =========================================================
-- DEVELOPMENT USERS
--
-- These are development/testing fixtures only.
--
-- Password for all three development accounts:
--   DevPassword123!
--
-- users contains every authenticated account.
--
-- CLIENT gets an additional clients row.
-- OPERATIONS and ANALYST do not.
-- =========================================================

INSERT INTO users (
    first_name,
    last_name,
    email,
    password_hash,
    role,
    status
)
VALUES
    (
        'Test',
        'Investor',
        'client.test@leap.local',
        '$2a$10$M0wjUzjz39GiE13s.IBTxerHSEYq7zJ4kEJ7rSittbiALpdeR7t1W',
        'CLIENT',
        'ACTIVE'
    ),
    (
        'Test',
        'Operations',
        'operations.test@leap.local',
        '$2a$10$M0wjUzjz39GiE13s.IBTxerHSEYq7zJ4kEJ7rSittbiALpdeR7t1W',
        'OPERATIONS',
        'ACTIVE'
    ),
    (
        'Test',
        'Analyst',
        'analyst.test@leap.local',
        '$2a$10$M0wjUzjz39GiE13s.IBTxerHSEYq7zJ4kEJ7rSittbiALpdeR7t1W',
        'ANALYST',
        'ACTIVE'
    )
ON CONFLICT (email) DO NOTHING;


-- =========================================================
-- CLIENT PROFILE
--
-- Only the CLIENT user receives a clients row.
--
-- clients.client_id is a shared primary key with
-- users.user_id.
-- =========================================================

INSERT INTO clients (
    client_id,
    date_of_birth,
    tax_id
)
SELECT
    u.user_id,
    '1995-01-15',
    'TEST-TAX-001'
FROM users u
WHERE u.email = 'client.test@leap.local'
  AND u.role = 'CLIENT'
ON CONFLICT DO NOTHING;


-- =========================================================
-- CLIENT PORTFOLIO
--
-- Exactly one portfolio for the development client.
-- =========================================================

INSERT INTO portfolios (
    client_id,
    cash_balance_usd
)
SELECT
    c.client_id,
    25000.00
FROM clients c
WHERE c.tax_id = 'TEST-TAX-001'
ON CONFLICT (client_id) DO NOTHING;


-- =========================================================
-- INITIAL CASH LEDGER ENTRY
--
-- Records the development client's starting $25,000 as an
-- initial modeled deposit so the cash ledger agrees with the
-- portfolio's starting cash balance.
-- =========================================================

INSERT INTO cash_transactions (
    portfolio_id,
    trade_id,
    transaction_type,
    amount_usd,
    balance_before_usd,
    balance_after_usd
)
SELECT
    p.portfolio_id,
    NULL,
    'DEPOSIT',
    25000.00,
    0.00,
    25000.00
FROM portfolios p
JOIN clients c
    ON c.client_id = p.client_id
WHERE c.tax_id = 'TEST-TAX-001'
  AND NOT EXISTS (
        SELECT 1
        FROM cash_transactions ct
        WHERE ct.portfolio_id = p.portfolio_id
          AND ct.transaction_type = 'DEPOSIT'
          AND ct.balance_before_usd = 0.00
          AND ct.balance_after_usd = 25000.00
    );


-- =========================================================
-- INITIAL MOCK PRICES
--
-- These bootstrap development only.
--
-- Future mock price updates should come from the
-- application's MockMarketDataProvider.
--
-- Each query identifies both symbol and market because
-- instrument symbols are only guaranteed unique within a
-- market, not globally.
-- =========================================================

INSERT INTO prices (
    instrument_id,
    price,
    price_currency_code,
    price_timestamp,
    source_type,
    provider_name
)
SELECT
    i.instrument_id,
    220.00,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'AAPL'
  AND m.market_name = 'NASDAQ'
  AND NOT EXISTS (
        SELECT 1
        FROM prices p
        WHERE p.instrument_id = i.instrument_id
          AND p.source_type = 'MOCK'
          AND p.provider_name = 'INTERNAL_MOCK'
    );


INSERT INTO prices (
    instrument_id,
    price,
    price_currency_code,
    price_timestamp,
    source_type,
    provider_name
)
SELECT
    i.instrument_id,
    0.75,
    'GBP',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'VOD'
  AND m.market_name = 'LSE'
  AND NOT EXISTS (
        SELECT 1
        FROM prices p
        WHERE p.instrument_id = i.instrument_id
          AND p.source_type = 'MOCK'
          AND p.provider_name = 'INTERNAL_MOCK'
    );


INSERT INTO prices (
    instrument_id,
    price,
    price_currency_code,
    price_timestamp,
    source_type,
    provider_name
)
SELECT
    i.instrument_id,
    1500.00,
    'INR',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'RELIANCE'
  AND m.market_name = 'NSE'
  AND NOT EXISTS (
        SELECT 1
        FROM prices p
        WHERE p.instrument_id = i.instrument_id
          AND p.source_type = 'MOCK'
          AND p.provider_name = 'INTERNAL_MOCK'
    );


INSERT INTO prices (
    instrument_id,
    price,
    price_currency_code,
    price_timestamp,
    source_type,
    provider_name
)
SELECT
    i.instrument_id,
    60000.00,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'BTC-USD'
  AND m.market_name = 'CRYPTO'
  AND NOT EXISTS (
        SELECT 1
        FROM prices p
        WHERE p.instrument_id = i.instrument_id
          AND p.source_type = 'MOCK'
          AND p.provider_name = 'INTERNAL_MOCK'
    );


INSERT INTO prices (
    instrument_id,
    price,
    price_currency_code,
    price_timestamp,
    source_type,
    provider_name
)
SELECT
    i.instrument_id,
    2500.00,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'ETH-USD'
  AND m.market_name = 'CRYPTO'
  AND NOT EXISTS (
        SELECT 1
        FROM prices p
        WHERE p.instrument_id = i.instrument_id
          AND p.source_type = 'MOCK'
          AND p.provider_name = 'INTERNAL_MOCK'
    );


INSERT INTO prices (
    instrument_id,
    price,
    price_currency_code,
    price_timestamp,
    source_type,
    provider_name
)
SELECT
    i.instrument_id,
    1.10,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'EUR/USD'
  AND m.market_name = 'FOREX'
  AND NOT EXISTS (
        SELECT 1
        FROM prices p
        WHERE p.instrument_id = i.instrument_id
          AND p.source_type = 'MOCK'
          AND p.provider_name = 'INTERNAL_MOCK'
    );


INSERT INTO prices (
    instrument_id,
    price,
    price_currency_code,
    price_timestamp,
    source_type,
    provider_name
)
SELECT
    i.instrument_id,
    1.35,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
JOIN markets m
    ON m.market_id = i.market_id
WHERE i.symbol = 'GBP/USD'
  AND m.market_name = 'FOREX'
  AND NOT EXISTS (
        SELECT 1
        FROM prices p
        WHERE p.instrument_id = i.instrument_id
          AND p.source_type = 'MOCK'
          AND p.provider_name = 'INTERNAL_MOCK'
    );


COMMIT;