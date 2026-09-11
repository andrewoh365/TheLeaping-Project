BEGIN;

-- =========================================================
-- CURRENCIES
-- Rates below are MOCK development values, not live rates.
-- The market-data layer can update these later.
-- =========================================================

INSERT INTO currency (
    currency_code,
    currency_name,
    currency_symbol,
    primary_country,
    current_exchange_rate_to_usd,
    exchange_rate_updated_at
)
VALUES
    ('USD', 'US Dollar', '$', 'United States', 1.0000000000, CURRENT_TIMESTAMP),
    ('GBP', 'British Pound', '£', 'United Kingdom', 1.3500000000, CURRENT_TIMESTAMP),
    ('INR', 'Indian Rupee', '₹', 'India', 0.0120000000, CURRENT_TIMESTAMP),
    ('EUR', 'Euro', '€', NULL, 1.1700000000, CURRENT_TIMESTAMP)
ON CONFLICT (currency_code) DO NOTHING;


-- =========================================================
-- MARKETS
-- =========================================================

INSERT INTO market (
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

INSERT INTO instrument (
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
        (SELECT market_id FROM market WHERE market_name = 'NASDAQ'),
        'AAPL',
        'Apple Inc.',
        'STOCK',
        'USD',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM market WHERE market_name = 'LSE'),
        'VOD',
        'Vodafone Group',
        'STOCK',
        'GBP',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM market WHERE market_name = 'NSE'),
        'RELIANCE',
        'Reliance Industries',
        'STOCK',
        'INR',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM market WHERE market_name = 'CRYPTO'),
        'BTC-USD',
        'Bitcoin',
        'CRYPTO',
        'USD',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM market WHERE market_name = 'CRYPTO'),
        'ETH-USD',
        'Ethereum',
        'CRYPTO',
        'USD',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM market WHERE market_name = 'FOREX'),
        'EUR/USD',
        'Euro / US Dollar',
        'FOREX',
        'USD',
        TRUE,
        TRUE
    ),

    (
        (SELECT market_id FROM market WHERE market_name = 'FOREX'),
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

INSERT INTO stock (
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
FROM instrument
WHERE symbol = 'AAPL'
AND NOT EXISTS (
    SELECT 1
    FROM stock s
    WHERE s.instrument_id = instrument.instrument_id
);


INSERT INTO stock (
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
FROM instrument
WHERE symbol = 'VOD'
AND NOT EXISTS (
    SELECT 1
    FROM stock s
    WHERE s.instrument_id = instrument.instrument_id
);


INSERT INTO stock (
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
FROM instrument
WHERE symbol = 'RELIANCE'
AND NOT EXISTS (
    SELECT 1
    FROM stock s
    WHERE s.instrument_id = instrument.instrument_id
);


-- =========================================================
-- CRYPTO DETAILS
-- =========================================================

INSERT INTO crypto (
    instrument_id,
    blockchain
)
SELECT
    instrument_id,
    'Bitcoin'
FROM instrument
WHERE symbol = 'BTC-USD'
AND NOT EXISTS (
    SELECT 1
    FROM crypto c
    WHERE c.instrument_id = instrument.instrument_id
);


INSERT INTO crypto (
    instrument_id,
    blockchain
)
SELECT
    instrument_id,
    'Ethereum'
FROM instrument
WHERE symbol = 'ETH-USD'
AND NOT EXISTS (
    SELECT 1
    FROM crypto c
    WHERE c.instrument_id = instrument.instrument_id
);


-- =========================================================
-- FOREX DETAILS
-- =========================================================

INSERT INTO forex (
    instrument_id,
    base_currency_code,
    quote_currency_code
)
SELECT
    instrument_id,
    'EUR',
    'USD'
FROM instrument
WHERE symbol = 'EUR/USD'
AND NOT EXISTS (
    SELECT 1
    FROM forex f
    WHERE f.instrument_id = instrument.instrument_id
);


INSERT INTO forex (
    instrument_id,
    base_currency_code,
    quote_currency_code
)
SELECT
    instrument_id,
    'GBP',
    'USD'
FROM instrument
WHERE symbol = 'GBP/USD'
AND NOT EXISTS (
    SELECT 1
    FROM forex f
    WHERE f.instrument_id = instrument.instrument_id
);


-- =========================================================
-- IMPORTED CLIENT FIXTURE
--
-- Represents:
-- imported client exists
-- -> email/password not registered yet
-- -> client later completes registration
-- =========================================================

INSERT INTO client (
    first_name,
    last_name,
    date_of_birth,
    tax_id,
    email,
    password_hash,
    role,
    status
)
VALUES (
    'Test',
    'Investor',
    '1995-01-15',
    'TEST-TAX-001',
    NULL,
    NULL,
    'CUSTOMER',
    'INACTIVE'
)
ON CONFLICT (tax_id) DO NOTHING;


-- =========================================================
-- ONE PORTFOLIO / SHARED USD CASH BANK
-- =========================================================

INSERT INTO portfolio (
    client_id,
    cash_balance_usd
)
SELECT
    client_id,
    25000.00
FROM client
WHERE tax_id = 'TEST-TAX-001'
AND NOT EXISTS (
    SELECT 1
    FROM portfolio p
    WHERE p.client_id = client.client_id
);


-- =========================================================
-- INITIAL MOCK PRICES
--
-- These only bootstrap development.
-- Future mock-price updates should come from the application's
-- MockMarketDataProvider.
-- =========================================================

INSERT INTO price (
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
FROM instrument
WHERE symbol = 'AAPL'
AND NOT EXISTS (
    SELECT 1
    FROM price p
    WHERE p.instrument_id = instrument.instrument_id
      AND p.source_type = 'MOCK'
      AND p.provider_name = 'INTERNAL_MOCK'
);


INSERT INTO price (
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
FROM instrument
WHERE symbol = 'VOD'
AND NOT EXISTS (
    SELECT 1
    FROM price p
    WHERE p.instrument_id = instrument.instrument_id
      AND p.source_type = 'MOCK'
      AND p.provider_name = 'INTERNAL_MOCK'
);


INSERT INTO price (
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
FROM instrument
WHERE symbol = 'RELIANCE'
AND NOT EXISTS (
    SELECT 1
    FROM price p
    WHERE p.instrument_id = instrument.instrument_id
      AND p.source_type = 'MOCK'
      AND p.provider_name = 'INTERNAL_MOCK'
);


INSERT INTO price (
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
FROM instrument
WHERE symbol = 'BTC-USD'
AND NOT EXISTS (
    SELECT 1
    FROM price p
    WHERE p.instrument_id = instrument.instrument_id
      AND p.source_type = 'MOCK'
      AND p.provider_name = 'INTERNAL_MOCK'
);


INSERT INTO price (
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
FROM instrument
WHERE symbol = 'ETH-USD'
AND NOT EXISTS (
    SELECT 1
    FROM price p
    WHERE p.instrument_id = instrument.instrument_id
      AND p.source_type = 'MOCK'
      AND p.provider_name = 'INTERNAL_MOCK'
);


INSERT INTO price (
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
FROM instrument
WHERE symbol = 'EUR/USD'
AND NOT EXISTS (
    SELECT 1
    FROM price p
    WHERE p.instrument_id = instrument.instrument_id
      AND p.source_type = 'MOCK'
      AND p.provider_name = 'INTERNAL_MOCK'
);


INSERT INTO price (
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
FROM instrument
WHERE symbol = 'GBP/USD'
AND NOT EXISTS (
    SELECT 1
    FROM price p
    WHERE p.instrument_id = instrument.instrument_id
      AND p.source_type = 'MOCK'
      AND p.provider_name = 'INTERNAL_MOCK'
);


COMMIT;