-- Reconcile local databases created from older schema revisions.
-- Adds the limit_price column expected by TradeOrder entity if it is missing.
ALTER TABLE IF EXISTS trade_orders
ADD COLUMN IF NOT EXISTS limit_price NUMERIC(20,8);
