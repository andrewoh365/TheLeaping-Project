
-- =========================================================
-- MOCK PRICE HISTORY
--
-- Add multiple price points for each instrument
-- to support chart display with historical data.
-- These are development/testing prices only.
-- =========================================================

BEGIN;

-- AAPL - Apple Inc. (USD)
INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 218.00, 'USD', CURRENT_TIMESTAMP - INTERVAL '29 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'AAPL' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '29 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 219.50, 'USD', CURRENT_TIMESTAMP - INTERVAL '24 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'AAPL' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '24 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 217.75, 'USD', CURRENT_TIMESTAMP - INTERVAL '19 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'AAPL' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '19 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 221.00, 'USD', CURRENT_TIMESTAMP - INTERVAL '14 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'AAPL' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '14 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 220.50, 'USD', CURRENT_TIMESTAMP - INTERVAL '9 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'AAPL' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '9 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 222.25, 'USD', CURRENT_TIMESTAMP - INTERVAL '4 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'AAPL' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '4 days'
);

-- VOD - Vodafone Group (GBP)
INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 0.73, 'GBP', CURRENT_TIMESTAMP - INTERVAL '29 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'VOD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '29 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 0.74, 'GBP', CURRENT_TIMESTAMP - INTERVAL '19 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'VOD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '19 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 0.72, 'GBP', CURRENT_TIMESTAMP - INTERVAL '9 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'VOD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '9 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 0.76, 'GBP', CURRENT_TIMESTAMP - INTERVAL '4 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'VOD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '4 days'
);

-- RELIANCE - Reliance Industries (INR)
INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1480.00, 'INR', CURRENT_TIMESTAMP - INTERVAL '29 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'RELIANCE' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '29 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1495.00, 'INR', CURRENT_TIMESTAMP - INTERVAL '19 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'RELIANCE' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '19 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1505.00, 'INR', CURRENT_TIMESTAMP - INTERVAL '9 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'RELIANCE' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '9 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1520.00, 'INR', CURRENT_TIMESTAMP - INTERVAL '4 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'RELIANCE' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '4 days'
);

-- BTC-USD - Bitcoin (USD)
INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 59500.00, 'USD', CURRENT_TIMESTAMP - INTERVAL '29 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'BTC-USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '29 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 61000.00, 'USD', CURRENT_TIMESTAMP - INTERVAL '19 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'BTC-USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '19 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 59800.00, 'USD', CURRENT_TIMESTAMP - INTERVAL '9 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'BTC-USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '9 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 62500.00, 'USD', CURRENT_TIMESTAMP - INTERVAL '4 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'BTC-USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '4 days'
);

-- ETH-USD - Ethereum (USD)
INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 2400.00, 'USD', CURRENT_TIMESTAMP - INTERVAL '29 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'ETH-USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '29 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 2550.00, 'USD', CURRENT_TIMESTAMP - INTERVAL '19 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'ETH-USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '19 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 2480.00, 'USD', CURRENT_TIMESTAMP - INTERVAL '9 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'ETH-USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '9 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 2620.00, 'USD', CURRENT_TIMESTAMP - INTERVAL '4 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'ETH-USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '4 days'
);

-- EUR/USD - Euro/Dollar (USD)
INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1.08, 'USD', CURRENT_TIMESTAMP - INTERVAL '29 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'EUR/USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '29 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1.09, 'USD', CURRENT_TIMESTAMP - INTERVAL '19 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'EUR/USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '19 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1.11, 'USD', CURRENT_TIMESTAMP - INTERVAL '9 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'EUR/USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '9 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1.12, 'USD', CURRENT_TIMESTAMP - INTERVAL '4 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'EUR/USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '4 days'
);

-- GBP/USD - Pound/Dollar (USD)
INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1.33, 'USD', CURRENT_TIMESTAMP - INTERVAL '29 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'GBP/USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '29 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1.34, 'USD', CURRENT_TIMESTAMP - INTERVAL '19 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'GBP/USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '19 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1.36, 'USD', CURRENT_TIMESTAMP - INTERVAL '9 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'GBP/USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '9 days'
);

INSERT INTO prices (instrument_id, price, price_currency_code, price_timestamp, source_type, provider_name)
SELECT i.instrument_id, 1.37, 'USD', CURRENT_TIMESTAMP - INTERVAL '4 days', 'MOCK', 'INTERNAL_MOCK'
FROM instruments i WHERE symbol = 'GBP/USD' AND NOT EXISTS (
  SELECT 1 FROM prices p WHERE p.instrument_id = i.instrument_id 
  AND p.price_timestamp = CURRENT_TIMESTAMP - INTERVAL '4 days'
);

COMMIT;
