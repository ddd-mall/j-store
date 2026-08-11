SET search_path TO develop, public;

ALTER TABLE user_accounts
    ALTER COLUMN id DROP DEFAULT,
    ALTER COLUMN phone_number TYPE VARCHAR(16);
