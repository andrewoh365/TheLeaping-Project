BEGIN;

-- =========================================================
-- CURRENCIES
-- Non-USD exchange rates can later come from mock/API data.
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
    ('GBP', 'British Pound', '£', 'United Kingdom', NULL, NULL),
    ('INR', 'Indian Rupee', '₹', 'India', NULL, NULL),
    ('EUR', 'Euro', '€', NULL, NULL)
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

COMMIT;