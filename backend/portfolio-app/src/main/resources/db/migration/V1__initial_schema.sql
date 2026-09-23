BEGIN;

-- =========================================================
-- USERS
--
-- Represents every authenticated account in the platform.
--
-- Roles:
--   CLIENT     -> retail client who can trade/view own account
--   OPERATIONS -> internal trading operations user
--   ANALYST    -> internal reporting/analytics user
--
-- A CLIENT additionally has a row in clients.
-- OPERATIONS and ANALYST users do not require separate
-- profile tables because they currently have no additional
-- role-specific persistent data.
-- =========================================================

CREATE TABLE users (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,

    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,

    role VARCHAR(20) NOT NULL
        CHECK (
            role IN (
                'CLIENT',
                'OPERATIONS',
                'ANALYST'
            )
        ),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (
            status IN (
                'ACTIVE',
                'INACTIVE',
                'LOCKED'
            )
        ),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMPTZ
);


-- =========================================================
-- CLIENTS
--
-- Client-specific business profile.
--
-- This is a shared-primary-key 1:1 relationship with users:
--
-- users.user_id = clients.client_id
--
-- Only users whose role is CLIENT should have a row here.
-- That role/profile consistency will also be enforced by the
-- application service layer.
-- =========================================================

CREATE TABLE clients (
    client_id BIGINT PRIMARY KEY,

    date_of_birth DATE NOT NULL,
    tax_id VARCHAR(100) NOT NULL UNIQUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_client_user
        FOREIGN KEY (client_id)
        REFERENCES users(user_id)
);


-- =========================================================
-- PORTFOLIOS
--
-- Exactly one portfolio per client.
-- Contains the client's shared USD cash balance.
-- =========================================================

CREATE TABLE portfolios (
    portfolio_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    client_id BIGINT NOT NULL UNIQUE,

    cash_balance_usd NUMERIC(20,2) NOT NULL DEFAULT 0
        CHECK (cash_balance_usd >= 0),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_portfolio_client
        FOREIGN KEY (client_id)
        REFERENCES clients(client_id)
);


-- =========================================================
-- MARKETS
-- =========================================================

CREATE TABLE markets (
    market_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    market_name VARCHAR(100) NOT NULL UNIQUE,
    country VARCHAR(100),

    timezone VARCHAR(100) NOT NULL,

    open_time TIME,
    close_time TIME,

    is_active BOOLEAN NOT NULL DEFAULT TRUE
);


-- =========================================================
-- CURRENCIES
--
-- current_exchange_rate_to_usd represents mutable current
-- state.
--
-- Historical rates used during executions are stored
-- separately with the execution/trade records.
-- =========================================================

CREATE TABLE currencies (
    currency_code VARCHAR(10) PRIMARY KEY,

    currency_name VARCHAR(100) NOT NULL,
    currency_symbol VARCHAR(10),

    current_exchange_rate_to_usd NUMERIC(20,10)
        CHECK (
            current_exchange_rate_to_usd IS NULL
            OR current_exchange_rate_to_usd > 0
        ),

    exchange_rate_updated_at TIMESTAMPTZ
);


-- =========================================================
-- INSTRUMENTS
-- =========================================================

CREATE TABLE instruments (
    instrument_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    market_id BIGINT NOT NULL,

    symbol VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,

    instrument_type VARCHAR(20) NOT NULL
        CHECK (
            instrument_type IN (
                'STOCK',
                'CRYPTO',
                'FOREX'
            )
        ),

    price_currency_code VARCHAR(10) NOT NULL,

    is_tradeable BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_instrument_market
        FOREIGN KEY (market_id)
        REFERENCES markets(market_id),

    CONSTRAINT fk_instrument_currency
        FOREIGN KEY (price_currency_code)
        REFERENCES currencies(currency_code),

    CONSTRAINT uq_instrument_market_symbol
        UNIQUE (market_id, symbol)
);


-- =========================================================
-- STOCKS
--
-- instrument_id is both PK and FK.
-- Creates a 1:1 extension of instruments for stock-specific
-- information.
-- =========================================================

CREATE TABLE stocks (
    instrument_id BIGINT PRIMARY KEY,

    sector VARCHAR(100),
    industry VARCHAR(100),
    country VARCHAR(100),

    CONSTRAINT fk_stock_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id)
);


-- =========================================================
-- CRYPTOS
-- =========================================================

CREATE TABLE cryptos (
    instrument_id BIGINT PRIMARY KEY,

    blockchain VARCHAR(100),

    CONSTRAINT fk_crypto_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id)
);


-- =========================================================
-- FOREXES
-- =========================================================

CREATE TABLE forexes (
    instrument_id BIGINT PRIMARY KEY,

    base_currency_code VARCHAR(10) NOT NULL,
    quote_currency_code VARCHAR(10) NOT NULL,

    CONSTRAINT fk_forex_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id),

    CONSTRAINT fk_forex_base_currency
        FOREIGN KEY (base_currency_code)
        REFERENCES currencies(currency_code),

    CONSTRAINT fk_forex_quote_currency
        FOREIGN KEY (quote_currency_code)
        REFERENCES currencies(currency_code),

    CONSTRAINT chk_forex_different_currencies
        CHECK (
            base_currency_code <> quote_currency_code
        ),

    CONSTRAINT uq_forex_pair
        UNIQUE (
            base_currency_code,
            quote_currency_code
        )
);


-- =========================================================
-- HOLDINGS
--
-- One current position per portfolio + instrument.
--
-- Quantity uses decimal precision because crypto and forex
-- positions may be fractional.
--
-- average_cost_usd is normalized to USD.
-- =========================================================

CREATE TABLE holdings (
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
        REFERENCES portfolios(portfolio_id),

    CONSTRAINT fk_holding_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id),

    CONSTRAINT uq_holding_portfolio_instrument
        UNIQUE (
            portfolio_id,
            instrument_id
        )
);


-- =========================================================
-- TRADE ORDERS
--
-- Represents the client's instruction/intent to trade.
--
-- This exists independently from execution so that an
-- accepted order remains recorded even if later execution
-- fails.
--
-- limit_price is expressed in the instrument's native
-- price_currency_code, NOT automatically in USD.
--
-- Example:
--   VOD priced in GBP -> limit_price is GBP
--   AAPL priced in USD -> limit_price is USD
-- =========================================================

CREATE TABLE trade_orders (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    portfolio_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,

    order_action VARCHAR(10) NOT NULL
        CHECK (
            order_action IN (
                'BUY',
                'SELL'
            )
        ),

    order_type VARCHAR(10) NOT NULL
        CHECK (
            order_type IN (
                'MARKET',
                'LIMIT'
            )
        ),

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
        CHECK (
            time_in_force IN (
                'DAY',
                'GTC'
            )
        ),

    quantity NUMERIC(30,10) NOT NULL
        CHECK (quantity > 0),

    limit_price NUMERIC(20,8),

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
        REFERENCES portfolios(portfolio_id),

    CONSTRAINT fk_order_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id),

    CONSTRAINT chk_limit_price
        CHECK (
            (
                order_type = 'MARKET'
                AND limit_price IS NULL
            )
            OR
            (
                order_type = 'LIMIT'
                AND limit_price IS NOT NULL
                AND limit_price > 0
            )
        )
);


-- =========================================================
-- EXECUTION ATTEMPTS
--
-- Records each attempt to execute an accepted order.
--
-- Multiple execution attempts may exist for an order.
--
-- FILLED or REJECTED means a pricing decision was made, so
-- quote information must be available.
--
-- FAILED may represent failure before a valid quote could be
-- obtained, so pricing fields may be NULL in that case.
-- =========================================================

CREATE TABLE execution_attempts (
    execution_attempt_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    order_id BIGINT NOT NULL,

    attempt_number INTEGER NOT NULL DEFAULT 1
        CHECK (attempt_number > 0),

    quoted_price NUMERIC(20,8)
        CHECK (
            quoted_price IS NULL
            OR quoted_price > 0
        ),

    price_currency_code VARCHAR(10),

    exchange_rate_to_usd NUMERIC(20,10)
        CHECK (
            exchange_rate_to_usd IS NULL
            OR exchange_rate_to_usd > 0
        ),

    quoted_price_usd NUMERIC(20,8)
        CHECK (
            quoted_price_usd IS NULL
            OR quoted_price_usd > 0
        ),

    quote_timestamp TIMESTAMPTZ,

    source_type VARCHAR(10)
        CHECK (
            source_type IN (
                'API',
                'MOCK'
            )
        ),

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
        REFERENCES trade_orders(order_id),

    CONSTRAINT fk_execution_currency
        FOREIGN KEY (price_currency_code)
        REFERENCES currencies(currency_code),

    CONSTRAINT uq_execution_order_attempt
        UNIQUE (
            order_id,
            attempt_number
        ),

    -- Required so Trade can reference the attempt together
    -- with the order and guarantee they belong together.
    CONSTRAINT uq_execution_attempt_order
        UNIQUE (
            execution_attempt_id,
            order_id
        ),

    CONSTRAINT chk_execution_pricing
        CHECK (
            attempt_status = 'FAILED'
            OR
            (
                quoted_price IS NOT NULL
                AND price_currency_code IS NOT NULL
                AND exchange_rate_to_usd IS NOT NULL
                AND quoted_price_usd IS NOT NULL
                AND quote_timestamp IS NOT NULL
                AND source_type IS NOT NULL
                AND provider_name IS NOT NULL
            )
        )
);


-- =========================================================
-- TRADES
--
-- Permanent completed execution record.
--
-- One completed fill per order for this project phase.
--
-- Native execution price and exchange rate are retained for
-- historical/audit reconstruction.
-- =========================================================

CREATE TABLE trades (
    trade_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    order_id BIGINT NOT NULL UNIQUE,
    execution_attempt_id BIGINT NOT NULL UNIQUE,

    quantity NUMERIC(30,10) NOT NULL
        CHECK (quantity > 0),

    execution_price NUMERIC(20,8) NOT NULL
        CHECK (execution_price > 0),

    execution_price_currency_code VARCHAR(10) NOT NULL,

    exchange_rate_to_usd_at_execution NUMERIC(20,10) NOT NULL
        CHECK (
            exchange_rate_to_usd_at_execution > 0
        ),

    execution_price_usd NUMERIC(20,8) NOT NULL
        CHECK (
            execution_price_usd > 0
        ),

    total_usd_value NUMERIC(30,8) NOT NULL
        CHECK (
            total_usd_value > 0
        ),

    executed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_trade_execution_attempt
        FOREIGN KEY (
            execution_attempt_id,
            order_id
        )
        REFERENCES execution_attempts(
            execution_attempt_id,
            order_id
        ),

    CONSTRAINT fk_trade_currency
        FOREIGN KEY (
            execution_price_currency_code
        )
        REFERENCES currencies(
            currency_code
        )
);


-- =========================================================
-- CASH TRANSACTIONS
--
-- Append-only cash ledger.
--
-- Cash balances settle to two decimal places.
-- Trade calculations may have greater precision; the
-- application layer must apply one consistent USD settlement
-- rounding rule when creating the cash transaction.
--
-- Payment/banking integration is outside the current phase,
-- but cash movements are still represented within the
-- platform.
-- =========================================================

CREATE TABLE cash_transactions (
    cash_transaction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    portfolio_id BIGINT NOT NULL,
    trade_id BIGINT,

    transaction_type VARCHAR(30) NOT NULL
        CHECK (
            transaction_type IN (
                'DEPOSIT',
                'WITHDRAWAL',
                'TRADE_BUY',
                'TRADE_SELL'
            )
        ),

    amount_usd NUMERIC(20,2) NOT NULL
        CHECK (
            amount_usd <> 0
        ),

    balance_before_usd NUMERIC(20,2) NOT NULL
        CHECK (
            balance_before_usd >= 0
        ),

    balance_after_usd NUMERIC(20,2) NOT NULL
        CHECK (
            balance_after_usd >= 0
        ),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cash_transaction_portfolio
        FOREIGN KEY (portfolio_id)
        REFERENCES portfolios(portfolio_id),

    CONSTRAINT fk_cash_transaction_trade
        FOREIGN KEY (trade_id)
        REFERENCES trades(trade_id),

    CONSTRAINT chk_cash_balance_math
        CHECK (
            balance_after_usd =
            balance_before_usd + amount_usd
        ),

    CONSTRAINT chk_cash_transaction_trade_link
        CHECK (
            (
                transaction_type IN (
                    'TRADE_BUY',
                    'TRADE_SELL'
                )
                AND trade_id IS NOT NULL
            )
            OR
            (
                transaction_type NOT IN (
                    'TRADE_BUY',
                    'TRADE_SELL'
                )
                AND trade_id IS NULL
            )
        ),

    CONSTRAINT chk_cash_transaction_direction
        CHECK (
            (
                transaction_type IN (
                    'DEPOSIT',
                    'TRADE_SELL'
                )
                AND amount_usd > 0
            )
            OR
            (
                transaction_type IN (
                    'WITHDRAWAL',
                    'TRADE_BUY'
                )
                AND amount_usd < 0
            )
        )
);


-- =========================================================
-- WATCHLISTS
--
-- Client-facing optional capability.
-- =========================================================

CREATE TABLE watchlists (
    watchlist_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    client_id BIGINT NOT NULL,
    watchlist_name VARCHAR(100) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_watchlist_client
        FOREIGN KEY (client_id)
        REFERENCES clients(client_id),

    CONSTRAINT uq_watchlist_client_name
        UNIQUE (
            client_id,
            watchlist_name
        )
);


-- =========================================================
-- WATCHLIST INSTRUMENTS
-- =========================================================

CREATE TABLE watchlist_instruments (
    watchlist_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,

    PRIMARY KEY (
        watchlist_id,
        instrument_id
    ),

    CONSTRAINT fk_watchlist_instrument_watchlist
        FOREIGN KEY (watchlist_id)
        REFERENCES watchlists(watchlist_id),

    CONSTRAINT fk_watchlist_instrument_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id)
);


-- =========================================================
-- AUDIT EVENTS
--
-- actor_user_id identifies the authenticated user responsible
-- for an action when there is one.
--
-- It may be NULL for system-generated events.
--
-- For client trading events, the affected client can be
-- determined through:
--
-- order -> portfolio -> client
--
-- JSONB allows event-specific structured information without
-- requiring a new table structure for every event type.
-- =========================================================

CREATE TABLE audit_events (
    audit_event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    actor_user_id BIGINT,

    order_id BIGINT,
    execution_attempt_id BIGINT,
    trade_id BIGINT,
    cash_transaction_id BIGINT,

    event_type VARCHAR(100) NOT NULL,

    event_details JSONB NOT NULL DEFAULT '{}'::JSONB,

    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_audit_order
        FOREIGN KEY (order_id)
        REFERENCES trade_orders(order_id),

    CONSTRAINT fk_audit_execution_attempt
        FOREIGN KEY (execution_attempt_id)
        REFERENCES execution_attempts(execution_attempt_id),

    CONSTRAINT fk_audit_trade
        FOREIGN KEY (trade_id)
        REFERENCES trades(trade_id),

    CONSTRAINT fk_audit_cash_transaction
        FOREIGN KEY (cash_transaction_id)
        REFERENCES cash_transactions(cash_transaction_id)
);


-- =========================================================
-- PRICES
--
-- Market-price observations.
--
-- Current execution pricing is captured again on the
-- execution_attempt so historical executions do not depend
-- on the latest value in this table.
-- =========================================================

CREATE TABLE prices (
    price_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    instrument_id BIGINT NOT NULL,

    price NUMERIC(20,8) NOT NULL
        CHECK (
            price > 0
        ),

    price_currency_code VARCHAR(10) NOT NULL,

    price_timestamp TIMESTAMPTZ NOT NULL,

    source_type VARCHAR(10) NOT NULL
        CHECK (
            source_type IN (
                'API',
                'MOCK'
            )
        ),

    provider_name VARCHAR(100) NOT NULL,

    CONSTRAINT fk_price_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id),

    CONSTRAINT fk_price_currency
        FOREIGN KEY (price_currency_code)
        REFERENCES currencies(currency_code)
);


-- =========================================================
-- INDEXES
--
-- PostgreSQL does not automatically create indexes for
-- ordinary foreign-key columns.
-- =========================================================

CREATE INDEX idx_order_portfolio
    ON trade_orders(portfolio_id);

CREATE INDEX idx_order_instrument
    ON trade_orders(instrument_id);

CREATE INDEX idx_order_status
    ON trade_orders(order_status);

CREATE INDEX idx_order_submitted_at
    ON trade_orders(submitted_at);

CREATE INDEX idx_execution_order
    ON execution_attempts(order_id);

CREATE INDEX idx_trade_executed_at
    ON trades(executed_at);

CREATE INDEX idx_cash_transaction_portfolio_created
    ON cash_transactions(
        portfolio_id,
        created_at
    );

CREATE INDEX idx_price_instrument_timestamp
    ON prices(
        instrument_id,
        price_timestamp DESC
    );

CREATE INDEX idx_audit_actor_user
    ON audit_events(actor_user_id);

CREATE INDEX idx_audit_order
    ON audit_events(order_id);

CREATE INDEX idx_audit_trade
    ON audit_events(trade_id);

CREATE INDEX idx_audit_occurred_at
    ON audit_events(occurred_at);


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


CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


CREATE TRIGGER trg_clients_updated_at
BEFORE UPDATE ON clients
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


CREATE TRIGGER trg_portfolios_updated_at
BEFORE UPDATE ON portfolios
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


CREATE TRIGGER trg_instruments_updated_at
BEFORE UPDATE ON instruments
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


CREATE TRIGGER trg_holdings_updated_at
BEFORE UPDATE ON holdings
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


CREATE TRIGGER trg_orders_updated_at
BEFORE UPDATE ON trade_orders
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


CREATE TRIGGER trg_watchlists_updated_at
BEFORE UPDATE ON watchlists
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


-- =========================================================
-- APPEND-ONLY PROTECTION
--
-- Execution attempts, trades, cash ledger records and audit
-- events represent historical facts.
--
-- They may not be updated or deleted after creation.
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


CREATE TRIGGER trg_execution_attempts_append_only
BEFORE UPDATE OR DELETE ON execution_attempts
FOR EACH ROW
EXECUTE FUNCTION prevent_append_only_mutation();


CREATE TRIGGER trg_trades_append_only
BEFORE UPDATE OR DELETE ON trades
FOR EACH ROW
EXECUTE FUNCTION prevent_append_only_mutation();


CREATE TRIGGER trg_cash_transactions_append_only
BEFORE UPDATE OR DELETE ON cash_transactions
FOR EACH ROW
EXECUTE FUNCTION prevent_append_only_mutation();


CREATE TRIGGER trg_audit_events_append_only
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW
EXECUTE FUNCTION prevent_append_only_mutation();


COMMIT;