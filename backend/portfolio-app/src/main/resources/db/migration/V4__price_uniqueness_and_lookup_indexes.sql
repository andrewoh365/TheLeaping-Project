-- Ensure historical price rows are unique per instrument, timestamp, and provider.
CREATE UNIQUE INDEX IF NOT EXISTS uq_prices_instrument_timestamp_provider
    ON prices(instrument_id, price_timestamp, provider_name);

-- Support history queries by symbol+time through instrument_id filtering.
CREATE INDEX IF NOT EXISTS idx_prices_timestamp
    ON prices(price_timestamp DESC);
