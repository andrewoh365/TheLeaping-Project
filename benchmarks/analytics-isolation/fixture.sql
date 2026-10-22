BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- These accounts exist only in the disposable benchmark database.
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
VALUES
    ('Benchmark', 'Analyst', 'benchmark.analyst@leap.test',
     crypt('BenchmarkAnalyst123!', gen_salt('bf', 10)), 'ANALYST', 'ACTIVE',
     CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days'),
    ('Benchmark', 'Trader 01', 'benchmark.trader01@leap.test',
     crypt('BenchmarkTrader123!', gen_salt('bf', 10)), 'CUSTOMER', 'ACTIVE',
     CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days'),
    ('Benchmark', 'Trader 02', 'benchmark.trader02@leap.test',
     crypt('BenchmarkTrader123!', gen_salt('bf', 10)), 'CUSTOMER', 'ACTIVE',
     CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days'),
    ('Benchmark', 'Trader 03', 'benchmark.trader03@leap.test',
     crypt('BenchmarkTrader123!', gen_salt('bf', 10)), 'CUSTOMER', 'ACTIVE',
     CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days'),
    ('Benchmark', 'Trader 04', 'benchmark.trader04@leap.test',
     crypt('BenchmarkTrader123!', gen_salt('bf', 10)), 'CUSTOMER', 'ACTIVE',
     CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days')
ON CONFLICT (email) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    user_type = EXCLUDED.user_type,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO analysts (user_id)
SELECT user_id FROM users WHERE email = 'benchmark.analyst@leap.test'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO customers (user_id, date_of_birth, tax_id)
SELECT user_id, DATE '1990-01-01', 'BENCH-TAX-' || LPAD(SUBSTRING(email FROM '([0-9]+)@'), 2, '0')
FROM users
WHERE email LIKE 'benchmark.trader%@leap.test'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO portfolios (customer_id, cash_balance_usd, created_at, updated_at)
SELECT user_id, 5000000.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users
WHERE email LIKE 'benchmark.trader%@leap.test'
ON CONFLICT (customer_id) DO UPDATE SET
    cash_balance_usd = 5000000.00,
    updated_at = CURRENT_TIMESTAMP;

COMMIT;
