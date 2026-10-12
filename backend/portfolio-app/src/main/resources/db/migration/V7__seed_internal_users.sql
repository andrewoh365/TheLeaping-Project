BEGIN;

-- =========================================================
-- V7: INTERNAL DEMO USERS
--
-- Development/demo accounts for testing internal RBAC.
--
-- ADMIN:
--   email: analytics.admin@leap.local
--   password: Admin123!
--
-- ANALYST:
--   email: analytics.analyst@leap.local
--   password: Analyst123!
--
-- These are DEMO credentials only and must not be used
-- in a production environment.
-- =========================================================


-- pgcrypto lets PostgreSQL create BCrypt-compatible hashes.
CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- =========================================================
-- ADMIN USER
-- =========================================================

INSERT INTO users (
    first_name,
    last_name,
    email,
    password_hash,
    user_type,
    status
)
VALUES (
    'Analytics',
    'Admin',
    'analytics.admin@leap.local',
    crypt('Admin123!', gen_salt('bf', 10)),
    'ADMIN',
    'ACTIVE'
)
ON CONFLICT (email)
DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    password_hash = EXCLUDED.password_hash,
    user_type = 'ADMIN',
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;


-- Remove incompatible subtype rows if this email was
-- previously created as another type during development.
DELETE FROM customers
WHERE user_id = (
    SELECT user_id
    FROM users
    WHERE email = 'analytics.admin@leap.local'
);

DELETE FROM analysts
WHERE user_id = (
    SELECT user_id
    FROM users
    WHERE email = 'analytics.admin@leap.local'
);


-- Ensure matching ADMIN child row exists.
INSERT INTO admins (user_id)
SELECT user_id
FROM users
WHERE email = 'analytics.admin@leap.local'
ON CONFLICT (user_id) DO NOTHING;


-- =========================================================
-- ANALYST USER
-- =========================================================

INSERT INTO users (
    first_name,
    last_name,
    email,
    password_hash,
    user_type,
    status
)
VALUES (
    'Analytics',
    'Analyst',
    'analytics.analyst@leap.local',
    crypt('Analyst123!', gen_salt('bf', 10)),
    'ANALYST',
    'ACTIVE'
)
ON CONFLICT (email)
DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    password_hash = EXCLUDED.password_hash,
    user_type = 'ANALYST',
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;


-- Remove incompatible subtype rows if needed.
DELETE FROM customers
WHERE user_id = (
    SELECT user_id
    FROM users
    WHERE email = 'analytics.analyst@leap.local'
);

DELETE FROM admins
WHERE user_id = (
    SELECT user_id
    FROM users
    WHERE email = 'analytics.analyst@leap.local'
);


-- Ensure matching ANALYST child row exists.
INSERT INTO analysts (user_id)
SELECT user_id
FROM users
WHERE email = 'analytics.analyst@leap.local'
ON CONFLICT (user_id) DO NOTHING;


COMMIT;