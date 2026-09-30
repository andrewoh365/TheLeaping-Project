BEGIN;

-- Restrict scope to business-required assets only.
-- This migration is a safe backfill for environments where seed data drifted.

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
        (SELECT market_id FROM markets WHERE market_name = 'NASDAQ'),
        'MSFT',
        'Microsoft Corporation',
        'STOCK',
        'USD',
        TRUE,
        TRUE
    ),
    (
        (SELECT market_id FROM markets WHERE market_name = 'NASDAQ'),
        'NVDA',
        'NVIDIA Corporation',
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
        (SELECT market_id FROM markets WHERE market_name = 'LSE'),
        'HSBA',
        'HSBC Holdings plc',
        'STOCK',
        'GBP',
        TRUE,
        TRUE
    ),
    (
        (SELECT market_id FROM markets WHERE market_name = 'LSE'),
        'BP',
        'BP p.l.c.',
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
        (SELECT market_id FROM markets WHERE market_name = 'NSE'),
        'TCS',
        'Tata Consultancy Services',
        'STOCK',
        'INR',
        TRUE,
        TRUE
    ),
    (
        (SELECT market_id FROM markets WHERE market_name = 'NSE'),
        'INFY',
        'Infosys Limited',
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
WHERE i.symbol = 'AAPL'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = i.instrument_id
);

INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    i.instrument_id,
    'Technology',
    'Software',
    'United States'
FROM instruments i
WHERE i.symbol = 'MSFT'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = i.instrument_id
);

INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    i.instrument_id,
    'Technology',
    'Semiconductors',
    'United States'
FROM instruments i
WHERE i.symbol = 'NVDA'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = i.instrument_id
);

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
WHERE i.symbol = 'VOD'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = i.instrument_id
);

INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    i.instrument_id,
    'Financial Services',
    'Banks - Diversified',
    'United Kingdom'
FROM instruments i
WHERE i.symbol = 'HSBA'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = i.instrument_id
);

INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    i.instrument_id,
    'Energy',
    'Oil & Gas Integrated',
    'United Kingdom'
FROM instruments i
WHERE i.symbol = 'BP'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = i.instrument_id
);

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
WHERE i.symbol = 'RELIANCE'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = i.instrument_id
);

INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    i.instrument_id,
    'Technology',
    'Information Technology Services',
    'India'
FROM instruments i
WHERE i.symbol = 'TCS'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = i.instrument_id
);

INSERT INTO stocks (
    instrument_id,
    sector,
    industry,
    country
)
SELECT
    i.instrument_id,
    'Technology',
    'Information Technology Services',
    'India'
FROM instruments i
WHERE i.symbol = 'INFY'
AND NOT EXISTS (
    SELECT 1
    FROM stocks s
    WHERE s.instrument_id = i.instrument_id
);

INSERT INTO cryptos (
    instrument_id,
    blockchain
)
SELECT
    i.instrument_id,
    'Bitcoin'
FROM instruments i
WHERE i.symbol = 'BTC-USD'
AND NOT EXISTS (
    SELECT 1
    FROM cryptos c
    WHERE c.instrument_id = i.instrument_id
);

INSERT INTO cryptos (
    instrument_id,
    blockchain
)
SELECT
    i.instrument_id,
    'Ethereum'
FROM instruments i
WHERE i.symbol = 'ETH-USD'
AND NOT EXISTS (
    SELECT 1
    FROM cryptos c
    WHERE c.instrument_id = i.instrument_id
);

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
WHERE i.symbol = 'EUR/USD'
AND NOT EXISTS (
    SELECT 1
    FROM forexes f
    WHERE f.instrument_id = i.instrument_id
);

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
WHERE i.symbol = 'GBP/USD'
AND NOT EXISTS (
    SELECT 1
    FROM forexes f
    WHERE f.instrument_id = i.instrument_id
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
    220.00,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
WHERE i.symbol = 'AAPL'
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
    430.00,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
WHERE i.symbol = 'MSFT'
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
    125.00,
    'USD',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
WHERE i.symbol = 'NVDA'
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
WHERE i.symbol = 'VOD'
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
    6.80,
    'GBP',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
WHERE i.symbol = 'HSBA'
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
    4.70,
    'GBP',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
WHERE i.symbol = 'BP'
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
WHERE i.symbol = 'RELIANCE'
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
    4200.00,
    'INR',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
WHERE i.symbol = 'TCS'
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
    1800.00,
    'INR',
    CURRENT_TIMESTAMP,
    'MOCK',
    'INTERNAL_MOCK'
FROM instruments i
WHERE i.symbol = 'INFY'
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
WHERE i.symbol = 'BTC-USD'
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
WHERE i.symbol = 'ETH-USD'
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
WHERE i.symbol = 'EUR/USD'
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
WHERE i.symbol = 'GBP/USD'
AND NOT EXISTS (
    SELECT 1
    FROM prices p
    WHERE p.instrument_id = i.instrument_id
      AND p.source_type = 'MOCK'
      AND p.provider_name = 'INTERNAL_MOCK'
);

COMMIT;
