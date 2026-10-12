-- =========================================================
-- V6__seed_analytics_dashboard_data.sql
--
-- Development/demo fixture for the internal analytics dashboard.
-- Adds enough historical customers, orders, fills, rejections,
-- execution attempts, trades, and audit events to exercise:
--   * date-range filtering
--   * asset-type filtering
--   * market filtering
--   * order-status filtering
--   * total trade volume
--   * total orders
--   * active clients
--   * fill rate
--   * trading volume over time
--   * order outcomes
--   * most-active instruments
--   * client activity trend
--   * top rejection reasons
--
-- IMPORTANT:
-- This is demo/seed data only. It intentionally does not alter
-- holdings, cash balances, watchlists, or live market-price history.
-- =========================================================

BEGIN;

-- =========================================================
-- 1. DASHBOARD CUSTOMER FIXTURES
-- =========================================================

CREATE TEMP TABLE dashboard_seed_customers (
    seed_customer_no INTEGER PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    date_of_birth DATE NOT NULL,
    tax_id VARCHAR(100) NOT NULL
) ON COMMIT DROP;

INSERT INTO dashboard_seed_customers (
    seed_customer_no,
    first_name,
    last_name,
    email,
    date_of_birth,
    tax_id
)
VALUES
    (1,  'Ava',    'Brooks',   'dashboard.client01@leap.test', '1992-03-14', 'DASH-TAX-001'),
    (2,  'Liam',   'Carter',   'dashboard.client02@leap.test', '1988-07-22', 'DASH-TAX-002'),
    (3,  'Maya',   'Chen',     'dashboard.client03@leap.test', '1995-11-05', 'DASH-TAX-003'),
    (4,  'Noah',   'Davis',    'dashboard.client04@leap.test', '1990-01-18', 'DASH-TAX-004'),
    (5,  'Sofia',  'Evans',    'dashboard.client05@leap.test', '1986-09-30', 'DASH-TAX-005'),
    (6,  'Ethan',  'Foster',   'dashboard.client06@leap.test', '1997-05-11', 'DASH-TAX-006'),
    (7,  'Amara',  'Green',    'dashboard.client07@leap.test', '1993-12-08', 'DASH-TAX-007'),
    (8,  'Lucas',  'Hall',     'dashboard.client08@leap.test', '1989-06-17', 'DASH-TAX-008'),
    (9,  'Nia',    'Jackson',  'dashboard.client09@leap.test', '1998-02-24', 'DASH-TAX-009'),
    (10, 'Oliver', 'King',     'dashboard.client10@leap.test', '1991-10-03', 'DASH-TAX-010'),
    (11, 'Zara',   'Lewis',    'dashboard.client11@leap.test', '1987-04-26', 'DASH-TAX-011'),
    (12, 'Mateo',  'Morgan',   'dashboard.client12@leap.test', '1994-08-15', 'DASH-TAX-012'),
    (13, 'Layla',  'Nelson',   'dashboard.client13@leap.test', '1996-01-29', 'DASH-TAX-013'),
    (14, 'Henry',  'Owens',    'dashboard.client14@leap.test', '1985-12-12', 'DASH-TAX-014'),
    (15, 'Imani',  'Parker',   'dashboard.client15@leap.test', '1999-07-07', 'DASH-TAX-015');

INSERT INTO users (
    first_name,
    last_name,
    email,
    password_hash,
    user_type,
    status,
    created_at,
    updated_at
)
SELECT
    first_name,
    last_name,
    email,
    NULL,
    'CUSTOMER',
    'ACTIVE',
    CURRENT_TIMESTAMP - INTERVAL '180 days',
    CURRENT_TIMESTAMP - INTERVAL '180 days'
FROM dashboard_seed_customers
ON CONFLICT (email) DO NOTHING;

INSERT INTO customers (
    user_id,
    date_of_birth,
    tax_id
)
SELECT
    u.user_id,
    s.date_of_birth,
    s.tax_id
FROM dashboard_seed_customers s
JOIN users u
    ON u.email = s.email
ON CONFLICT (tax_id) DO NOTHING;

INSERT INTO portfolios (
    customer_id,
    cash_balance_usd,
    created_at,
    updated_at
)
SELECT
    u.user_id,
    500000.00,
    CURRENT_TIMESTAMP - INTERVAL '180 days',
    CURRENT_TIMESTAMP - INTERVAL '180 days'
FROM dashboard_seed_customers s
JOIN users u
    ON u.email = s.email
JOIN customers c
    ON c.user_id = u.user_id
ON CONFLICT (customer_id) DO NOTHING;


-- =========================================================
-- 2. PLAN 280 HISTORICAL ORDERS
--
-- Activity intentionally increases over time:
--   first 40 days:   ~1 order/day
--   next 40 days:    ~2 orders/day
--   most recent 40:  ~4 orders/day
--
-- Approximate outcome mix:
--   70% FILLED
--   15% REJECTED
--    5% CANCELLED
--    5% ACCEPTED
--    5% SUBMITTED
--
-- Instrument weighting intentionally makes AAPL, MSFT,
-- and BTC-USD the most active instruments.
-- =========================================================

CREATE TEMP TABLE dashboard_seed_order_plan (
    seed_order_no INTEGER PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    portfolio_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    instrument_type VARCHAR(20) NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    price_currency_code VARCHAR(10) NOT NULL,
    order_action VARCHAR(10) NOT NULL,
    order_type VARCHAR(10) NOT NULL,
    order_status VARCHAR(20) NOT NULL,
    quantity NUMERIC(30,10) NOT NULL,
    limit_price NUMERIC(20,8),
    submitted_at TIMESTAMPTZ NOT NULL,
    rejection_reason TEXT,
    execution_price NUMERIC(20,8) NOT NULL,
    exchange_rate_to_usd NUMERIC(20,10) NOT NULL,
    order_id BIGINT
) ON COMMIT DROP;

WITH base AS (
    SELECT
        n,
        ((n - 1) / 20) AS block_index,
        MOD(n, 20) AS outcome_slot,
        MOD(n * 7 + ((n - 1) / 20), 20) AS instrument_slot,
        MOD(n * 7 + (((n - 1) / 20) * 3), 20) + 1 AS customer_slot,

        CASE
            WHEN n <= 40 THEN
                CURRENT_TIMESTAMP
                - INTERVAL '120 days'
                + ((n - 1) * INTERVAL '1 day')
            WHEN n <= 120 THEN
                CURRENT_TIMESTAMP
                - INTERVAL '80 days'
                + ((n - 41) * INTERVAL '12 hours')
            ELSE
                CURRENT_TIMESTAMP
                - INTERVAL '40 days'
                + ((n - 121) * INTERVAL '6 hours')
        END AS submitted_at
    FROM generate_series(1, 280) AS gs(n)
),
generated AS (
    SELECT
        n AS seed_order_no,
        block_index,

        CASE
            WHEN customer_slot <= 15 THEN customer_slot
            ELSE customer_slot - 15
        END AS seed_customer_no,

        CASE
            WHEN instrument_slot BETWEEN 0 AND 4 THEN 'AAPL'
            WHEN instrument_slot BETWEEN 5 AND 7 THEN 'MSFT'
            WHEN instrument_slot BETWEEN 8 AND 9 THEN 'BTC-USD'
            WHEN instrument_slot = 10 THEN 'NVDA'
            WHEN instrument_slot = 11 THEN 'VOD'
            WHEN instrument_slot = 12 THEN 'HSBA'
            WHEN instrument_slot = 13 THEN 'BP'
            WHEN instrument_slot = 14 THEN 'RELIANCE'
            WHEN instrument_slot = 15 THEN 'TCS'
            WHEN instrument_slot = 16 THEN 'INFY'
            WHEN instrument_slot = 17 THEN 'ETH-USD'
            WHEN instrument_slot = 18 THEN 'EUR/USD'
            ELSE 'GBP/USD'
        END AS symbol,

        CASE
            WHEN outcome_slot BETWEEN 0 AND 13 THEN 'FILLED'
            WHEN outcome_slot BETWEEN 14 AND 16 THEN 'REJECTED'
            WHEN outcome_slot = 17 THEN 'CANCELLED'
            WHEN outcome_slot = 18 THEN 'ACCEPTED'
            ELSE 'SUBMITTED'
        END AS order_status,

        CASE
            WHEN MOD(n, 3) = 0 THEN 'SELL'
            ELSE 'BUY'
        END AS order_action,

        CASE
            WHEN MOD(n, 4) = 0 THEN 'LIMIT'
            ELSE 'MARKET'
        END AS order_type,

        submitted_at
    FROM base
),
resolved AS (
    SELECT
        g.*,
        u.user_id AS customer_id,
        pf.portfolio_id,
        i.instrument_id,
        i.instrument_type,
        i.price_currency_code,
        px.price AS base_price,
        c.current_exchange_rate_to_usd AS exchange_rate_to_usd
    FROM generated g
    JOIN dashboard_seed_customers dsc
        ON dsc.seed_customer_no = g.seed_customer_no
    JOIN users u
        ON u.email = dsc.email
    JOIN portfolios pf
        ON pf.customer_id = u.user_id
    JOIN instruments i
        ON i.symbol = g.symbol
    JOIN currencies c
        ON c.currency_code = i.price_currency_code
    JOIN LATERAL (
        SELECT p.price
        FROM prices p
        WHERE p.instrument_id = i.instrument_id
        ORDER BY p.price_timestamp DESC, p.price_id DESC
        LIMIT 1
    ) px ON TRUE
)
INSERT INTO dashboard_seed_order_plan (
    seed_order_no,
    customer_id,
    portfolio_id,
    instrument_id,
    instrument_type,
    symbol,
    price_currency_code,
    order_action,
    order_type,
    order_status,
    quantity,
    limit_price,
    submitted_at,
    rejection_reason,
    execution_price,
    exchange_rate_to_usd
)
SELECT
    seed_order_no,
    customer_id,
    portfolio_id,
    instrument_id,
    instrument_type,
    symbol,
    price_currency_code,
    order_action,
    order_type,
    order_status,

    CASE
        WHEN instrument_type = 'STOCK'
            THEN (MOD(seed_order_no, 8) + 1)::NUMERIC(30,10)
        WHEN instrument_type = 'CRYPTO'
            THEN ((MOD(seed_order_no, 10) + 1) * 0.01)::NUMERIC(30,10)
        ELSE
            (500 + (MOD(seed_order_no, 20) * 100))::NUMERIC(30,10)
    END AS quantity,

    CASE
        WHEN order_type = 'LIMIT'
            THEN ROUND(
                base_price
                * CASE
                    WHEN order_action = 'BUY' THEN 1.015
                    ELSE 0.985
                  END,
                8
            )
        ELSE NULL
    END AS limit_price,

    submitted_at,

    CASE
        WHEN order_status = 'REJECTED' THEN
            CASE MOD(block_index, 5)
                WHEN 0 THEN 'INSUFFICIENT_CASH'
                WHEN 1 THEN 'INSUFFICIENT_HOLDINGS'
                WHEN 2 THEN 'MARKET_CLOSED'
                WHEN 3 THEN 'PRICE_UNAVAILABLE'
                ELSE 'INSTRUMENT_NOT_TRADABLE'
            END
        ELSE NULL
    END AS rejection_reason,

    ROUND(
        base_price
        * (
            1
            + ((MOD(seed_order_no, 7) - 3) * 0.0025)
          ),
        8
    ) AS execution_price,

    exchange_rate_to_usd
FROM resolved;


-- =========================================================
-- 3. INSERT ORDERS
-- =========================================================

INSERT INTO trade_orders (
    portfolio_id,
    instrument_id,
    order_action,
    order_type,
    order_status,
    time_in_force,
    quantity,
    limit_price,
    submitted_at,
    accepted_at,
    rejected_at,
    rejection_reason,
    cancelled_at,
    filled_at,
    created_at,
    updated_at
)
SELECT
    portfolio_id,
    instrument_id,
    order_action,
    order_type,
    order_status,
    CASE
        WHEN MOD(seed_order_no, 5) = 0 THEN 'GTC'
        ELSE 'DAY'
    END,
    quantity,
    limit_price,
    submitted_at,

    CASE
        WHEN order_status IN ('FILLED', 'ACCEPTED', 'CANCELLED')
            THEN submitted_at + INTERVAL '2 seconds'
        ELSE NULL
    END AS accepted_at,

    CASE
        WHEN order_status = 'REJECTED'
            THEN submitted_at + INTERVAL '2 seconds'
        ELSE NULL
    END AS rejected_at,

    rejection_reason,

    CASE
        WHEN order_status = 'CANCELLED'
            THEN submitted_at + INTERVAL '15 minutes'
        ELSE NULL
    END AS cancelled_at,

    CASE
        WHEN order_status = 'FILLED'
            THEN submitted_at + INTERVAL '8 seconds'
        ELSE NULL
    END AS filled_at,

    submitted_at,

    CASE
        WHEN order_status = 'FILLED'
            THEN submitted_at + INTERVAL '8 seconds'
        WHEN order_status = 'REJECTED'
            THEN submitted_at + INTERVAL '2 seconds'
        WHEN order_status = 'CANCELLED'
            THEN submitted_at + INTERVAL '15 minutes'
        WHEN order_status = 'ACCEPTED'
            THEN submitted_at + INTERVAL '2 seconds'
        ELSE submitted_at
    END AS updated_at
FROM dashboard_seed_order_plan;


-- Map the generated database IDs back to the temporary plan.
-- The seed timestamps are unique within this migration.
UPDATE dashboard_seed_order_plan p
SET order_id = o.order_id
FROM trade_orders o
WHERE o.portfolio_id = p.portfolio_id
  AND o.instrument_id = p.instrument_id
  AND o.submitted_at = p.submitted_at
  AND o.created_at = p.submitted_at;


-- =========================================================
-- 4. EXECUTION ATTEMPTS FOR FILLED ORDERS
-- =========================================================

INSERT INTO execution_attempts (
    order_id,
    attempt_number,
    quoted_price,
    price_currency_code,
    exchange_rate_to_usd,
    quoted_price_usd,
    quote_timestamp,
    source_type,
    provider_name,
    attempt_status,
    reason,
    attempted_at
)
SELECT
    order_id,
    1,
    execution_price,
    price_currency_code,
    exchange_rate_to_usd,
    ROUND(execution_price * exchange_rate_to_usd, 8),
    submitted_at + INTERVAL '5 seconds',
    'MOCK',
    'DASHBOARD_SEED',
    'FILLED',
    NULL,
    submitted_at + INTERVAL '5 seconds'
FROM dashboard_seed_order_plan
WHERE order_status = 'FILLED';


-- =========================================================
-- 5. TRADES FOR FILLED ORDERS
-- =========================================================

INSERT INTO trades (
    order_id,
    execution_attempt_id,
    quantity,
    execution_price,
    execution_price_currency_code,
    exchange_rate_to_usd_at_execution,
    execution_price_usd,
    total_usd_value,
    executed_at
)
SELECT
    p.order_id,
    ea.execution_attempt_id,
    p.quantity,
    p.execution_price,
    p.price_currency_code,
    p.exchange_rate_to_usd,
    ROUND(p.execution_price * p.exchange_rate_to_usd, 8),
    ROUND(
        p.quantity
        * p.execution_price
        * p.exchange_rate_to_usd,
        8
    ),
    p.submitted_at + INTERVAL '8 seconds'
FROM dashboard_seed_order_plan p
JOIN execution_attempts ea
    ON ea.order_id = p.order_id
   AND ea.attempt_number = 1
WHERE p.order_status = 'FILLED';


-- =========================================================
-- 6. AUDIT EVENTS
--
-- These are useful later for the Admin / Head of Trading
-- Operations drill-down and order lifecycle timeline.
-- =========================================================

INSERT INTO audit_events (
    user_id,
    order_id,
    event_type,
    event_details,
    occurred_at
)
SELECT
    p.customer_id,
    p.order_id,
    'ORDER_SUBMITTED',
    jsonb_build_object(
        'seed', true,
        'symbol', p.symbol,
        'action', p.order_action,
        'quantity', p.quantity
    ),
    p.submitted_at
FROM dashboard_seed_order_plan p;

INSERT INTO audit_events (
    user_id,
    order_id,
    event_type,
    event_details,
    occurred_at
)
SELECT
    p.customer_id,
    p.order_id,
    'ORDER_ACCEPTED',
    jsonb_build_object(
        'seed', true,
        'symbol', p.symbol
    ),
    p.submitted_at + INTERVAL '2 seconds'
FROM dashboard_seed_order_plan p
WHERE p.order_status IN ('FILLED', 'ACCEPTED', 'CANCELLED');

INSERT INTO audit_events (
    user_id,
    order_id,
    event_type,
    event_details,
    occurred_at
)
SELECT
    p.customer_id,
    p.order_id,
    'ORDER_REJECTED',
    jsonb_build_object(
        'seed', true,
        'symbol', p.symbol,
        'reason', p.rejection_reason
    ),
    p.submitted_at + INTERVAL '2 seconds'
FROM dashboard_seed_order_plan p
WHERE p.order_status = 'REJECTED';

INSERT INTO audit_events (
    user_id,
    order_id,
    event_type,
    event_details,
    occurred_at
)
SELECT
    p.customer_id,
    p.order_id,
    'ORDER_CANCELLED',
    jsonb_build_object(
        'seed', true,
        'symbol', p.symbol
    ),
    p.submitted_at + INTERVAL '15 minutes'
FROM dashboard_seed_order_plan p
WHERE p.order_status = 'CANCELLED';

INSERT INTO audit_events (
    user_id,
    order_id,
    execution_attempt_id,
    event_type,
    event_details,
    occurred_at
)
SELECT
    p.customer_id,
    p.order_id,
    ea.execution_attempt_id,
    'PRICE_CAPTURED',
    jsonb_build_object(
        'seed', true,
        'symbol', p.symbol,
        'quotedPrice', ea.quoted_price,
        'currency', ea.price_currency_code,
        'provider', ea.provider_name
    ),
    ea.quote_timestamp
FROM dashboard_seed_order_plan p
JOIN execution_attempts ea
    ON ea.order_id = p.order_id
   AND ea.attempt_number = 1
WHERE p.order_status = 'FILLED';

INSERT INTO audit_events (
    user_id,
    order_id,
    execution_attempt_id,
    trade_id,
    event_type,
    event_details,
    occurred_at
)
SELECT
    p.customer_id,
    p.order_id,
    ea.execution_attempt_id,
    t.trade_id,
    'TRADE_FILLED',
    jsonb_build_object(
        'seed', true,
        'symbol', p.symbol,
        'quantity', t.quantity,
        'executionPrice', t.execution_price,
        'totalUsdValue', t.total_usd_value
    ),
    t.executed_at
FROM dashboard_seed_order_plan p
JOIN execution_attempts ea
    ON ea.order_id = p.order_id
   AND ea.attempt_number = 1
JOIN trades t
    ON t.order_id = p.order_id
WHERE p.order_status = 'FILLED';

COMMIT;
