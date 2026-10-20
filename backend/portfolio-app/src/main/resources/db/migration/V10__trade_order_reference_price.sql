-- Market price of the instrument when the order was submitted (BR-11).
-- Recorded with the order terms and never updated. It is the price the pre-trade
-- checks used, and what order history shows for orders that did not fill.
ALTER TABLE trade_orders
ADD COLUMN IF NOT EXISTS reference_price_usd NUMERIC(20,8)
    CHECK (
        reference_price_usd IS NULL
        OR reference_price_usd > 0
    );

-- Backfill existing orders that never filled, from the last price at or before
-- submission (or the earliest price after it, if history doesn't go back that far).
-- Converted to USD at the currency's current rate, as MarketPriceService does.
UPDATE trade_orders o
SET reference_price_usd = (
    SELECT ROUND(p.price * c.current_exchange_rate_to_usd, 8)
    FROM prices p
    JOIN currencies c ON c.currency_code = p.price_currency_code
    WHERE p.instrument_id = o.instrument_id
      AND c.current_exchange_rate_to_usd > 0
    ORDER BY
        (p.price_timestamp > o.submitted_at),
        CASE WHEN p.price_timestamp <= o.submitted_at THEN o.submitted_at - p.price_timestamp END,
        p.price_timestamp
    LIMIT 1
)
WHERE o.reference_price_usd IS NULL
  AND o.order_status <> 'FILLED';
