-- =========================================================
-- V8__seed_demo_holdings.sql
--
-- Adds demo starting positions for selected dashboard clients.
--
-- These holdings are intentionally independent from the
-- historical analytics orders seeded in V6. V6 does not
-- modify holdings or cash balances.
--
-- average_cost_usd is always stored in USD, even when the
-- instrument itself is priced in another currency.
-- =========================================================

BEGIN;

WITH holding_seed (
    email,
    symbol,
    quantity,
    average_cost_usd
) AS (
    VALUES

        -- Ava Brooks
        (
            'dashboard.client01@leap.test',
            'AAPL',
            40.0000000000,
            190.00000000
        ),
        (
            'dashboard.client01@leap.test',
            'VOD',
            800.0000000000,
            0.92000000
        ),
        (
            'dashboard.client01@leap.test',
            'BTC-USD',
            0.0800000000,
            55000.00000000
        ),
        (
            'dashboard.client01@leap.test',
            'EUR/USD',
            5000.0000000000,
            1.06000000
        ),

        -- Liam Carter
        (
            'dashboard.client02@leap.test',
            'MSFT',
            12.0000000000,
            450.00000000
        ),
        (
            'dashboard.client02@leap.test',
            'NVDA',
            25.0000000000,
            105.00000000
        ),
        (
            'dashboard.client02@leap.test',
            'ETH-USD',
            1.5000000000,
            2800.00000000
        ),

        -- Maya Chen
        (
            'dashboard.client03@leap.test',
            'AAPL',
            20.0000000000,
            205.00000000
        ),
        (
            'dashboard.client03@leap.test',
            'BP',
            500.0000000000,
            5.90000000
        ),
        (
            'dashboard.client03@leap.test',
            'TCS',
            60.0000000000,
            48.00000000
        ),

        -- Sofia Evans
        (
            'dashboard.client05@leap.test',
            'HSBA',
            300.0000000000,
            8.70000000
        ),
        (
            'dashboard.client05@leap.test',
            'RELIANCE',
            150.0000000000,
            20.00000000
        ),
        (
            'dashboard.client05@leap.test',
            'GBP/USD',
            4000.0000000000,
            1.30000000
        ),

        -- Mateo Morgan
        (
            'dashboard.client12@leap.test',
            'MSFT',
            8.0000000000,
            400.00000000
        ),
        (
            'dashboard.client12@leap.test',
            'INFY',
            200.0000000000,
            22.50000000
        ),
        (
            'dashboard.client12@leap.test',
            'ETH-USD',
            0.7500000000,
            2300.00000000
        )
)

INSERT INTO holdings (
    portfolio_id,
    instrument_id,
    quantity,
    average_cost_usd,
    updated_at
)
SELECT
    p.portfolio_id,
    i.instrument_id,
    s.quantity,
    s.average_cost_usd,
    CURRENT_TIMESTAMP
FROM holding_seed s
JOIN users u
    ON u.email = s.email
JOIN customers c
    ON c.user_id = u.user_id
JOIN portfolios p
    ON p.customer_id = c.user_id
JOIN instruments i
    ON i.symbol = s.symbol
ON CONFLICT (
    portfolio_id,
    instrument_id
)
DO NOTHING;

COMMIT;