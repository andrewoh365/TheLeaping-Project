CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_username ON users(username);
