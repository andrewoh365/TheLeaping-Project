BEGIN;

-- A signed-in session has an absolute end time and can be revoked
-- before that end, so a stolen credential stops working.

CREATE TABLE user_sessions (
    session_id UUID PRIMARY KEY,

    user_id BIGINT NOT NULL,

    refresh_token_hash VARCHAR(64) NOT NULL,

    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,

    CONSTRAINT fk_user_session_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE,

    CONSTRAINT uq_user_session_refresh_hash
        UNIQUE (refresh_token_hash)
);

CREATE INDEX idx_user_sessions_user_id
    ON user_sessions(user_id);

COMMIT;
