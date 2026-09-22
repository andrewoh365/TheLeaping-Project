BEGIN;

-- =========================================================
-- CURRENCIES
-- Rates below are MOCK development values, not live rates.
-- The market-data layer can update these later
-- =========================================================

INSERT INTO currencies (
    currency_code,
    currency_name,
    currency_symbol,
    current_exchange_rate_to_usd,
    exchange_rate_updated_at
)
VALUES
    ('USD', 'US Dollar', '$', 1.0000000000, CURRENT_TIMESTAMP),
    ('GBP', 'British Pound', '£', 1.3500000000, CURRENT_TIMESTAMP),
    ('INR', 'Indian Rupee', '₹', 0.0120000000, CURRENT_TIMESTAMP),
    ('EUR', 'Euro', '€', 1.1700000000, CURRENT_TIMESTAMP)
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
-- At least one example from each major market/asset class.
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
        (SELECT market_id FROM markets WHERE market_name = 'NASDAQ'),
        'AAPL',
        'Apple Inc.',
        'STOCK',
        'USD',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM markets WHERE market_name = 'LSE'),
        'VOD',
        'Vodafone Group',
        'STOCK',
        'GBP',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM markets WHERE market_name = 'NSE'),
        'RELIANCE',
        'Reliance Industries',
        'STOCK',
        'INR',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM markets WHERE market_name = 'CRYPTO'),
        'BTC-USD',
        'Bitcoin',
        'CRYPTO',
        'USD',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM markets WHERE market_name = 'CRYPTO'),
        'ETH-USD',
        'Ethereum',
        'CRYPTO',
        'USD',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM markets WHERE market_name = 'FOREX'),
        'EUR/USD',
        'Euro / US Dollar',
        'FOREX',
        'USD',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM markets WHERE market_name = 'FOREX'),
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
    instrument_id,
    'Technology',
    'Consumer Electronics',
    'United States'
FROM instruments
WHERE symbol = 'AAPL'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = instruments.instrument_id
);


INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    instrument_id,
    'Communication Services',
    'Telecommunications',
    'United Kingdom'
FROM instruments
WHERE symbol = 'VOD'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = instruments.instrument_id
);


INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    instrument_id,
    'Energy',
    'Diversified',
    'India'
FROM instruments
WHERE symbol = 'RELIANCE'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = instruments.instrument_id
);


-- =========================================================
-- CRYPTO DETAILS
-- =========================================================

INSERT INTO cryptos (
    instrument_id,
    blockchain
)
SELECT
    instrument_id,
    'Bitcoin'
FROM instruments
WHERE symbol = 'BTC-USD'
AND NOT EXISTS (
    SELECT 1
    FROM cryptos c
    WHERE c.instrument_id = instruments.instrument_id
);


INSERT INTO cryptos (
    instrument_id,
    blockchain
)
SELECT
    instrument_id,
    'Ethereum'
FROM instruments
WHERE symbol = 'ETH-USD'
AND NOT EXISTS (
    SELECT 1
    FROM cryptos c
    WHERE c.instrument_id = instruments.instrument_id
);


-- =========================================================
-- FOREX DETAILS
-- =========================================================

INSERT INTO forexes (
    instrument_id,
    base_currency_code,
    quote_currency_code
)
SELECT
    instrument_id,
    'EUR',
    'USD'
FROM instruments
WHERE symbol = 'EUR/USD'
AND NOT EXISTS (
    SELECT 1
    FROM forexes f
    WHERE f.instrument_id = instruments.instrument_id
);


INSERT INTO forexes (
    instrument_id,
    base_currency_code,
    quote_currency_code
)
SELECT
    instrument_id,
    'GBP',
    'USD'
FROM instruments
WHERE symbol = 'GBP/USD'
AND NOT EXISTS (
    SELECT 1
    FROM forexes f
    WHERE f.instrument_id = instruments.instrument_id
);


-- =========================================================
-- IMPORTED CUSTOMER FIXTURE
--
-- Represents:
-- imported customer exists
-- -> email/password not registered yet
-- -> customer later completes registration
--
-- Inserted as USERS + CUSTOMERS together via a single CTE so
-- the two rows stay in sync: tax_id (on customers) is the
-- idempotency key, since email is NULL until registration.
-- =========================================================

WITH new_user AS (
    INSERT INTO users (
        first_name,
        last_name,
        email,
        password_hash,
        user_type,
        status
    )
    SELECT
        'Test',
        'Investor',
        NULL,
        NULL,
        'CUSTOMER',
        'INACTIVE'
    WHERE NOT EXISTS (
        SELECT 1 FROM customers WHERE tax_id = 'TEST-TAX-001'
    )
    RETURNING user_id
)
INSERT INTO customers (
    user_id,
    date_of_birth,
    tax_id
)
SELECT
    user_id,
    '1995-01-15',
    'TEST-TAX-001'
FROM new_user;


-- =========================================================
-- ONE PORTFOLIO / SHARED USD CASH BANK
-- =========================================================

INSERT INTO portfolios (
    customer_id,
    cash_balance_usd
)
SELECT
    user_id,
    25000.00
FROM customers
WHERE tax_id = 'TEST-TAX-001'
AND NOT EXISTS (
    SELECT 1
    FROM portfolios p
    WHERE p.customer_id = customers.user_id
);


-- =========================================================
-- INITIAL MOCK PRICES
--
-- These only bootstrap development.
-- Future mock-price updates should come from the application's
-- MockMarketDataProvider.
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
    instrument_id,
    220.00,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments
WHERE symbol = 'AAPL'
AND NOT EXISTS (
    SELECT 1
    FROM prices p
    WHERE p.instrument_id = instruments.instrument_id
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
    instrument_id,
    0.75,
    'GBP',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments
WHERE symbol = 'VOD'
AND NOT EXISTS (
    SELECT 1
    FROM prices p
    WHERE p.instrument_id = instruments.instrument_id
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
    instrument_id,
    1500.00,
    'INR',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments
WHERE symbol = 'RELIANCE'
AND NOT EXISTS (
    SELECT 1
    FROM prices p
    WHERE p.instrument_id = instruments.instrument_id
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
    instrument_id,
    60000.00,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments
WHERE symbol = 'BTC-USD'
AND NOT EXISTS (
    SELECT 1
    FROM prices p
    WHERE p.instrument_id = instruments.instrument_id
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
    instrument_id,
    2500.00,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments
WHERE symbol = 'ETH-USD'
AND NOT EXISTS (
    SELECT 1
    FROM prices p
    WHERE p.instrument_id = instruments.instrument_id
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
    instrument_id,
    1.10,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments
WHERE symbol = 'EUR/USD'
AND NOT EXISTS (
    SELECT 1
    FROM prices p
    WHERE p.instrument_id = instruments.instrument_id
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
    instrument_id,
    1.35,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments
WHERE symbol = 'GBP/USD'
AND NOT EXISTS (
    SELECT 1
    FROM prices p
    WHERE p.instrument_id = instruments.instrument_id
      AND p.source_type = 'MOCK'
      AND p.provider_name = 'INTERNAL_MOCK'
);


COMMIT;
