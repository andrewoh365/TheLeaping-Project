-- =========================================================
-- V14: NAME DEMO ACCOUNTS BY THEIR ROLE
--
-- The login page's quick-login buttons are labelled by role.
-- Rename the seeded demo accounts so the name shown in the
-- app matches the button. Emails and passwords are unchanged.
--
--   Investor   -> john.trader@example.com
--   Analyst    -> analytics.analyst@leap.local
--   Operations -> analytics.admin@leap.local
--
-- john.trader@example.com is created by DataSeeder (dev profile),
-- which already uses the new name; this UPDATE covers databases
-- seeded before that change.
-- =========================================================

UPDATE users
SET first_name = 'Investor',
    last_name = '',
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'john.trader@example.com';

UPDATE users
SET first_name = 'Analyst',
    last_name = '',
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'analytics.analyst@leap.local';

UPDATE users
SET first_name = 'Operations',
    last_name = '',
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'analytics.admin@leap.local';
