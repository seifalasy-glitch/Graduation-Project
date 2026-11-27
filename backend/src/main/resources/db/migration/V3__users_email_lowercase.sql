-- V3: emails are stored in lower case, and Ali@x and ali@x can't be two accounts.
-- The backend also lowercases the email on login and forgot-password.
--
-- If this fails with a unique-index error, two accounts differ only by letter case.
-- Find them with:
--   SELECT lower(email), count(*) FROM users GROUP BY lower(email) HAVING count(*) > 1;
--
-- Safe to run on a database where this was already applied by hand.

UPDATE users
SET email = lower(email)
WHERE email <> lower(email);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower
    ON users (lower(email));
