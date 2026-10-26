-- =========================================================
-- V13: NAME DEMO ACCOUNTS AFTER THE BRS PERSONAS
--
-- The login page's quick-login buttons map the three BRS
-- personas (section 6) to seeded demo accounts. Rename those
-- accounts so the name shown in the app matches the persona.
-- Emails and passwords are unchanged.
--
--   Joanna (self-directed investor) -> john.trader@example.com
--   Priya  (commercial analyst)      -> analytics.analyst@leap.local
--   David  (head of trading ops)     -> analytics.admin@leap.local
--
-- john.trader@example.com is created by DataSeeder (dev profile),
-- which already uses the new name; this UPDATE covers databases
-- seeded before that change. Last names are intentionally blank
-- so only the first name is displayed.
-- =========================================================

UPDATE users
SET first_name = 'Joanna',
    last_name = '',
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'john.trader@example.com';

UPDATE users
SET first_name = 'Priya',
    last_name = '',
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'analytics.analyst@leap.local';

UPDATE users
SET first_name = 'David',
    last_name = '',
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'analytics.admin@leap.local';
