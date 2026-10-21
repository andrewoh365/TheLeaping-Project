-- Market reference price at order submission (used for order history and replay).
ALTER TABLE trade_orders
ADD COLUMN IF NOT EXISTS reference_price_usd NUMERIC(20,8)
    CHECK (
        reference_price_usd IS NULL
        OR reference_price_usd > 0
    );

-- Backfill existing non-filled orders from the nearest available historical print.
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
