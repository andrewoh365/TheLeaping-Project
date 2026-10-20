ALTER TABLE trade_orders
    ADD COLUMN IF NOT EXISTS expired_at TIMESTAMPTZ;

ALTER TABLE trade_orders
    DROP CONSTRAINT IF EXISTS trade_orders_order_status_check;

ALTER TABLE trade_orders
    ADD CONSTRAINT trade_orders_order_status_check
    CHECK (order_status IN ('SUBMITTED', 'ACCEPTED', 'FILLED', 'REJECTED', 'CANCELLED', 'EXPIRED'));