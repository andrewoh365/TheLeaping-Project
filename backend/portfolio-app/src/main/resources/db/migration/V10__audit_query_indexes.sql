CREATE INDEX idx_audit_user_occurred_at
    ON audit_events(user_id, occurred_at, audit_event_id);

CREATE INDEX idx_audit_order_occurred_at
    ON audit_events(order_id, occurred_at, audit_event_id);