DROP TABLE IF EXISTS audit;
DROP TABLE IF EXISTS trades;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS holdings;
DROP TABLE IF EXISTS instruments;
DROP TABLE IF EXISTS currency;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS clients;


CREATE TABLE clients(
    client_id SERIAL PRIMARY KEY,
    first_name TEXT NOT NULL, 
    last_name TEXT NOT NULL, 
    email TEXT NOT NULL UNIQUE,
    password_hash CHAR(60) UNIQUE,
    segment TEXT NOT NULL CHECK (segment IN ('RETAIL','PREMIUM','INSTITUTIONAL')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE accounts(
    account_id SERIAL PRIMARY KEY,
    client_id INTEGER NOT NULL references clients(client_id),
    account_type TEXT NOT NULL CHECK (account_type IN ('CASH','MARGIN','RETIREMENT')),
    cash_balance NUMERIC(14,4) NOT NULL CHECK (cash_balance >= 0),
    status TEXT NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_accounts_client_id ON accounts(client_id);

CREATE TABLE currency(
    currency_id SERIAL PRIMARY KEY,
    currency_code CHAR(3) NOT NULL UNIQUE CHECK (currency_code ~ '^[A-Z]{3}$'),
    currency_name TEXT NOT NULL,
    currency_symbol TEXT NOT NULL,
    primary_country CHAR(10) NOT NULL,
    current_exchange_rate NUMERIC(14,4) NOT NULL CHECK (current_exchange_rate >= 0),
    exchange_rate_last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE instruments(
    instrument_id SERIAL PRIMARY KEY,
    currency_id INTEGER NOT NULL REFERENCES currency(currency_id),
    instrument_type TEXT NOT NULL CHECK (instrument_type IN ('EQUITY','BOND','ETF','MUTUAL_FUND','INDEX')),
    tradeable BOOLEAN NOT NULL,
    market TEXT NOT NULL,
    ticker TEXT NOT NULL UNIQUE,
    company_name TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_instruments_currency ON instruments(currency_id);


CREATE TABLE holdings(
    holding_id  SERIAL PRIMARY KEY,
    instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),
    account_id INTEGER NOT NULL REFERENCES accounts(account_id),
    holding_type TEXT NOT NULL,
    quantity NUMERIC(14,4) NOT NULL CHECK (quantity >= 0),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_holdings_instrument_id ON holdings(instrument_id);
CREATE INDEX idx_holdings_account_id ON holdings(account_id);

CREATE TABLE orders(
    order_id SERIAL PRIMARY KEY,
    account_id INTEGER NOT NULL references accounts(account_id),
    instrument_id INTEGER NOT NULL references instruments(instrument_id),
    order_type TEXT NOT NULL CHECK (order_type IN ('BUY','SELL')),
    quantity NUMERIC(14,4) NOT NULL CHECK (quantity >= 0),
    price NUMERIC(14,4) NOT NULL CHECK (price >= 0),
    status TEXT NOT NULL CHECK (status IN ('PENDING','ACCEPTED','EXECUTED','CANCELLED','REJECTED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP CHECK (accepted_at IS NULL OR accepted_at >= created_at),
    executed_at TIMESTAMP CHECK (executed_at IS NULL OR executed_at >= accepted_at)
);
CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_instrument_id ON orders(instrument_id);
CREATE INDEX idx_orders_status ON orders(status) WHERE status = 'PENDING';

CREATE TABLE trades(
    trade_id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL references orders(order_id),
    account_id INTEGER NOT NULL references accounts(account_id),
    instrument_id INTEGER NOT NULL references instruments(instrument_id),
    quantity NUMERIC(14,4) NOT NULL CHECK (quantity >= 0),
    executed_price_currency CHAR(3) NOT NULL REFERENCES currency(currency_code),
    executed_price NUMERIC(14,4) NOT NULL CHECK (executed_price >= 0),
    status TEXT NOT NULL CHECK (status IN ('EXECUTED','FAILED','REVERSED')),
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_trades_order_id ON trades(order_id);
CREATE INDEX idx_trades_account_id ON trades(account_id);
CREATE INDEX idx_trades_instrument_id ON trades(instrument_id);
CREATE INDEX idx_trades_account_executed_at ON trades(account_id, executed_at DESC);


CREATE TABLE audit(
    audit_id SERIAL PRIMARY KEY,
    client_id INTEGER NOT NULL references clients(client_id),
    account_id INTEGER NOT NULL references accounts(account_id),
    audit_event_type TEXT NOT NULL,
    audit_event_details TEXT NOT NULL,
    audit_event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_client_id ON audit(client_id);
CREATE INDEX idx_audit_account_id ON audit(account_id);
CREATE INDEX idx_audit_account_timestamp ON audit(account_id, audit_event_timestamp DESC);

