-- "Lanjutkan dengan Google" (5 Nov 2026): the Google account's stable id. A Google-only account has no password_hash.
ALTER TABLE users ADD COLUMN google_sub text;
CREATE UNIQUE INDEX users_google_sub_key ON users (google_sub) WHERE google_sub IS NOT NULL;
-- A Google-only account has an email and no password.
ALTER TABLE users DROP CONSTRAINT users_email_needs_password;
ALTER TABLE users ADD CONSTRAINT users_email_needs_password CHECK (
    (email IS NULL) = (password_hash IS NULL) OR (email IS NOT NULL AND google_sub IS NOT NULL)
);
