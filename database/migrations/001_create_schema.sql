BEGIN;

-- =========================================================
-- CLIENT
-- Imported clients may initially have NULL email/password.
-- Registration populates email + password_hash.
-- =========================================================

CREATE TABLE client (
    client_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,

    tax_id VARCHAR(100) NOT NULL UNIQUE,

    email VARCHAR(255) UNIQUE,
    password_hash TEXT,

    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER'
        CHECK (role IN ('ADMIN', 'CUSTOMER')),

    status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED')),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMPTZ
);


-- =========================================================
-- PORTFOLIO
-- Exactly one portfolio per client.
-- Contains the client's one shared USD cash bank.
-- =========================================================

CREATE TABLE portfolio (
    portfolio_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    client_id BIGINT NOT NULL UNIQUE,

    cash_balance_usd NUMERIC(20,2) NOT NULL DEFAULT 0
        CHECK (cash_balance_usd >= 0),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_portfolio_client
        FOREIGN KEY (client_id)
        REFERENCES client(client_id)
);


-- =========================================================
-- MARKET
-- =========================================================

CREATE TABLE market (
    market_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    market_name VARCHAR(100) NOT NULL UNIQUE,
    country VARCHAR(100),

    timezone VARCHAR(100) NOT NULL,

    open_time TIME,
    close_time TIME,

    is_active BOOLEAN NOT NULL DEFAULT TRUE
);


-- =========================================================
-- CURRENCY
-- Current exchange rate is mutable current-state data.
-- Historical rates used for trades are captured separately.
-- =========================================================

CREATE TABLE currency (
    currency_code VARCHAR(10) PRIMARY KEY,

    currency_name VARCHAR(100) NOT NULL,
    currency_symbol VARCHAR(10),
    primary_country VARCHAR(100),

    current_exchange_rate_to_usd NUMERIC(20,10)
        CHECK (
            current_exchange_rate_to_usd IS NULL
            OR current_exchange_rate_to_usd > 0
        ),

    exchange_rate_updated_at TIMESTAMPTZ
);


-- =========================================================
-- INSTRUMENT
-- =========================================================

CREATE TABLE instrument (
    instrument_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    market_id BIGINT NOT NULL,

    symbol VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,

    instrument_type VARCHAR(20) NOT NULL
        CHECK (
            instrument_type IN ('STOCK', 'CRYPTO', 'FOREX')
        ),

    price_currency_code VARCHAR(10) NOT NULL,

    is_tradeable BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_instrument_market
        FOREIGN KEY (market_id)
        REFERENCES market(market_id),

    CONSTRAINT fk_instrument_currency
        FOREIGN KEY (price_currency_code)
        REFERENCES currency(currency_code),

    CONSTRAINT uq_instrument_market_symbol
        UNIQUE (market_id, symbol)
);


-- =========================================================
-- STOCK DETAILS
-- instrument_id is both PK and FK, creating a true 1:1
-- relationship with Instrument.
-- =========================================================

CREATE TABLE stock (
    instrument_id BIGINT PRIMARY KEY,

    sector VARCHAR(100),
    industry VARCHAR(100),
    country VARCHAR(100),

    CONSTRAINT fk_stock_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instrument(instrument_id)
);


-- =========================================================
-- CRYPTO DETAILS
-- =========================================================

CREATE TABLE crypto (
    instrument_id BIGINT PRIMARY KEY,

    blockchain VARCHAR(100),

    CONSTRAINT fk_crypto_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instrument(instrument_id)
);


-- =========================================================
-- FOREX DETAILS
-- =========================================================

CREATE TABLE forex (
    instrument_id BIGINT PRIMARY KEY,

    base_currency_code VARCHAR(10) NOT NULL,
    quote_currency_code VARCHAR(10) NOT NULL,

    CONSTRAINT fk_forex_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instrument(instrument_id),

    CONSTRAINT fk_forex_base_currency
        FOREIGN KEY (base_currency_code)
        REFERENCES currency(currency_code),

    CONSTRAINT fk_forex_quote_currency
        FOREIGN KEY (quote_currency_code)
        REFERENCES currency(currency_code),

    CONSTRAINT chk_forex_different_currencies
        CHECK (base_currency_code <> quote_currency_code),

    CONSTRAINT uq_forex_pair
        UNIQUE (base_currency_code, quote_currency_code)
);


-- =========================================================
-- HOLDING
-- One current position per portfolio + instrument.
-- Quantity is decimal because crypto/forex may be fractional.
-- Average cost is normalized to USD.
-- =========================================================

CREATE TABLE holding (
    holding_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    portfolio_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,

    quantity NUMERIC(30,10) NOT NULL
        CHECK (quantity >= 0),

    average_cost_usd NUMERIC(20,8) NOT NULL DEFAULT 0
        CHECK (average_cost_usd >= 0),

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_holding_portfolio
        FOREIGN KEY (portfolio_id)
        REFERENCES portfolio(portfolio_id),

    CONSTRAINT fk_holding_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instrument(instrument_id),

    CONSTRAINT uq_holding_portfolio_instrument
        UNIQUE (portfolio_id, instrument_id)
);


-- =========================================================
-- TRADE ORDER
-- Do not use a table literally named ORDER because ORDER
-- is a SQL keyword.
-- =========================================================

CREATE TABLE trade_order (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    portfolio_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,

    order_action VARCHAR(10) NOT NULL
        CHECK (order_action IN ('BUY', 'SELL')),

    order_type VARCHAR(10) NOT NULL
        CHECK (order_type IN ('MARKET', 'LIMIT')),

    order_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED'
        CHECK (
            order_status IN (
                'SUBMITTED',
                'ACCEPTED',
                'FILLED',
                'REJECTED',
                'CANCELLED'
            )
        ),

    time_in_force VARCHAR(10) NOT NULL
        CHECK (time_in_force IN ('DAY', 'GTC')),

    quantity NUMERIC(30,10) NOT NULL
        CHECK (quantity > 0),

    limit_price_usd NUMERIC(20,8),

    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    rejection_reason TEXT,
    cancelled_at TIMESTAMPTZ,
    filled_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_portfolio
        FOREIGN KEY (portfolio_id)
        REFERENCES portfolio(portfolio_id),

    CONSTRAINT fk_order_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instrument(instrument_id),

    CONSTRAINT chk_limit_price
        CHECK (
            (order_type = 'MARKET' AND limit_price_usd IS NULL)
            OR
            (order_type = 'LIMIT'
             AND limit_price_usd IS NOT NULL
             AND limit_price_usd > 0)
        )
);


-- =========================================================
-- EXECUTION ATTEMPT
-- Records the quote/pricing decision even if no trade occurs.
-- Multiple attempts may exist for one accepted order.
-- =========================================================

CREATE TABLE execution_attempt (
    execution_attempt_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    order_id BIGINT NOT NULL,

    attempt_number INTEGER NOT NULL DEFAULT 1
        CHECK (attempt_number > 0),

    quoted_price NUMERIC(20,8),
    price_currency_code VARCHAR(10),

    exchange_rate_to_usd NUMERIC(20,10)
        CHECK (
            exchange_rate_to_usd IS NULL
            OR exchange_rate_to_usd > 0
        ),

    quoted_price_usd NUMERIC(20,8),

    quote_timestamp TIMESTAMPTZ,

    source_type VARCHAR(10)
        CHECK (source_type IN ('API', 'MOCK')),

    provider_name VARCHAR(100),

    attempt_status VARCHAR(20) NOT NULL
        CHECK (
            attempt_status IN (
                'FILLED',
                'REJECTED',
                'FAILED'
            )
        ),

    reason TEXT,

    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_execution_order
        FOREIGN KEY (order_id)
        REFERENCES trade_order(order_id),

    CONSTRAINT fk_execution_currency
        FOREIGN KEY (price_currency_code)
        REFERENCES currency(currency_code),

    CONSTRAINT uq_execution_order_attempt
        UNIQUE (order_id, attempt_number),

    CONSTRAINT uq_execution_attempt_order
        UNIQUE (execution_attempt_id, order_id)
);


-- =========================================================
-- TRADE
-- One completed fill for an order in this phase.
-- Native execution price + exchange rate are retained for
-- audit, while total value is normalized to USD.
-- =========================================================

CREATE TABLE trade (
    trade_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    order_id BIGINT NOT NULL UNIQUE,
    execution_attempt_id BIGINT NOT NULL UNIQUE,

    quantity NUMERIC(30,10) NOT NULL
        CHECK (quantity > 0),

    execution_price NUMERIC(20,8) NOT NULL
        CHECK (execution_price > 0),

    execution_price_currency_code VARCHAR(10) NOT NULL,

    exchange_rate_to_usd_at_execution NUMERIC(20,10) NOT NULL
        CHECK (exchange_rate_to_usd_at_execution > 0),

    execution_price_usd NUMERIC(20,8) NOT NULL
        CHECK (execution_price_usd > 0),

    total_usd_value NUMERIC(30,8) NOT NULL
        CHECK (total_usd_value > 0),

    executed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_trade_execution_attempt
        FOREIGN KEY (execution_attempt_id, order_id)
        REFERENCES execution_attempt(execution_attempt_id, order_id),

    CONSTRAINT fk_trade_currency
        FOREIGN KEY (execution_price_currency_code)
        REFERENCES currency(currency_code)
);


-- =========================================================
-- CASH TRANSACTION
-- Same table handles stocks, crypto and forex.
-- Trade itself tells us which instrument was involved.
-- =========================================================

CREATE TABLE cash_transaction (
    cash_transaction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    portfolio_id BIGINT NOT NULL,
    trade_id BIGINT,

    transaction_type VARCHAR(30) NOT NULL
        CHECK (
            transaction_type IN (
                'DEPOSIT',
                'WITHDRAWAL',
                'TRADE_BUY',
                'TRADE_SELL',
                'ADMIN_ADJUSTMENT'
            )
        ),

    amount_usd NUMERIC(20,2) NOT NULL
        CHECK (amount_usd <> 0),

    balance_before_usd NUMERIC(20,2) NOT NULL
        CHECK (balance_before_usd >= 0),

    balance_after_usd NUMERIC(20,2) NOT NULL
        CHECK (balance_after_usd >= 0),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cash_transaction_portfolio
        FOREIGN KEY (portfolio_id)
        REFERENCES portfolio(portfolio_id),

    CONSTRAINT fk_cash_transaction_trade
        FOREIGN KEY (trade_id)
        REFERENCES trade(trade_id),

    CONSTRAINT chk_cash_balance_math
        CHECK (
            balance_after_usd =
            balance_before_usd + amount_usd
        ),

    CONSTRAINT chk_cash_transaction_trade_link
        CHECK (
            (
                transaction_type IN ('TRADE_BUY', 'TRADE_SELL')
                AND trade_id IS NOT NULL
            )
            OR
            (
                transaction_type NOT IN ('TRADE_BUY', 'TRADE_SELL')
                AND trade_id IS NULL
            )
        ),

    CONSTRAINT chk_cash_transaction_direction
        CHECK (
            (transaction_type IN ('DEPOSIT', 'TRADE_SELL')
                AND amount_usd > 0)
            OR
            (transaction_type IN ('WITHDRAWAL', 'TRADE_BUY')
                AND amount_usd < 0)
            OR
            (transaction_type = 'ADMIN_ADJUSTMENT')
        )
);


-- =========================================================
-- WATCHLIST
-- =========================================================

CREATE TABLE watchlist (
    watchlist_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    client_id BIGINT NOT NULL,
    watchlist_name VARCHAR(100) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_watchlist_client
        FOREIGN KEY (client_id)
        REFERENCES client(client_id),

    CONSTRAINT uq_watchlist_client_name
        UNIQUE (client_id, watchlist_name)
);


CREATE TABLE watchlist_instrument (
    watchlist_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,

    PRIMARY KEY (watchlist_id, instrument_id),

    CONSTRAINT fk_watchlist_instrument_watchlist
        FOREIGN KEY (watchlist_id)
        REFERENCES watchlist(watchlist_id),

    CONSTRAINT fk_watchlist_instrument_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instrument(instrument_id)
);


-- =========================================================
-- AUDIT EVENT
-- JSONB gives different event types flexible structured data.
-- =========================================================

CREATE TABLE audit_event (
    audit_event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    client_id BIGINT,

    order_id BIGINT,
    execution_attempt_id BIGINT,
    trade_id BIGINT,
    cash_transaction_id BIGINT,

    event_type VARCHAR(100) NOT NULL,

    event_details JSONB NOT NULL DEFAULT '{}'::JSONB,

    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_client
        FOREIGN KEY (client_id)
        REFERENCES client(client_id),

    CONSTRAINT fk_audit_order
        FOREIGN KEY (order_id)
        REFERENCES trade_order(order_id),

    CONSTRAINT fk_audit_execution_attempt
        FOREIGN KEY (execution_attempt_id)
        REFERENCES execution_attempt(execution_attempt_id),

    CONSTRAINT fk_audit_trade
        FOREIGN KEY (trade_id)
        REFERENCES trade(trade_id),

    CONSTRAINT fk_audit_cash_transaction
        FOREIGN KEY (cash_transaction_id)
        REFERENCES cash_transaction(cash_transaction_id)
);


-- =========================================================
-- PRICE HISTORY
-- =========================================================

CREATE TABLE price (
    price_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    instrument_id BIGINT NOT NULL,

    price NUMERIC(20,8) NOT NULL
        CHECK (price > 0),

    price_currency_code VARCHAR(10) NOT NULL,

    price_timestamp TIMESTAMPTZ NOT NULL,

    source_type VARCHAR(10) NOT NULL
        CHECK (source_type IN ('API', 'MOCK')),

    provider_name VARCHAR(100) NOT NULL,

    CONSTRAINT fk_price_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instrument(instrument_id),

    CONSTRAINT fk_price_currency
        FOREIGN KEY (price_currency_code)
        REFERENCES currency(currency_code)
);


-- =========================================================
-- INDEXES
-- Foreign keys are not automatically indexed by PostgreSQL.
-- =========================================================

CREATE INDEX idx_order_portfolio
    ON trade_order(portfolio_id);

CREATE INDEX idx_order_instrument
    ON trade_order(instrument_id);

CREATE INDEX idx_order_status
    ON trade_order(order_status);

CREATE INDEX idx_order_submitted_at
    ON trade_order(submitted_at);

CREATE INDEX idx_execution_order
    ON execution_attempt(order_id);

CREATE INDEX idx_trade_executed_at
    ON trade(executed_at);

CREATE INDEX idx_cash_transaction_portfolio_created
    ON cash_transaction(portfolio_id, created_at);

CREATE INDEX idx_price_instrument_timestamp
    ON price(instrument_id, price_timestamp DESC);

CREATE INDEX idx_audit_order
    ON audit_event(order_id);

CREATE INDEX idx_audit_trade
    ON audit_event(trade_id);

CREATE INDEX idx_audit_occurred_at
    ON audit_event(occurred_at);


-- =========================================================
-- AUTOMATIC updated_at HANDLING
-- =========================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


CREATE TRIGGER trg_client_updated_at
BEFORE UPDATE ON client
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_portfolio_updated_at
BEFORE UPDATE ON portfolio
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_instrument_updated_at
BEFORE UPDATE ON instrument
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_holding_updated_at
BEFORE UPDATE ON holding
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_order_updated_at
BEFORE UPDATE ON trade_order
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_watchlist_updated_at
BEFORE UPDATE ON watchlist
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


-- =========================================================
-- APPEND-ONLY PROTECTION
-- Execution attempts, trades, cash ledger records and audit
-- events cannot be updated or deleted after creation.
-- =========================================================

CREATE OR REPLACE FUNCTION prevent_append_only_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'Table % is append-only. UPDATE and DELETE are not allowed.',
        TG_TABLE_NAME;
END;
$$;


CREATE TRIGGER trg_execution_attempt_append_only
BEFORE UPDATE OR DELETE ON execution_attempt
FOR EACH ROW
EXECUTE FUNCTION prevent_append_only_mutation();

CREATE TRIGGER trg_trade_append_only
BEFORE UPDATE OR DELETE ON trade
FOR EACH ROW
EXECUTE FUNCTION prevent_append_only_mutation();

CREATE TRIGGER trg_cash_transaction_append_only
BEFORE UPDATE OR DELETE ON cash_transaction
FOR EACH ROW
EXECUTE FUNCTION prevent_append_only_mutation();

CREATE TRIGGER trg_audit_event_append_only
BEFORE UPDATE OR DELETE ON audit_event
FOR EACH ROW
EXECUTE FUNCTION prevent_append_only_mutation();


COMMIT;