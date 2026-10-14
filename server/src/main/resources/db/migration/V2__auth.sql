-- =====================================================================
-- V2: Phase 3 — sign-in (email + password) and refresh tokens
-- =====================================================================

-- Email may be NULL only for the old dummy user from Phase 2.
-- New accounts from /auth/register always have it set by the server.
ALTER TABLE users
    ADD COLUMN email          TEXT,
    ADD COLUMN password_hash     TEXT,          -- bcrypt, NEVER store the plain password
    ADD COLUMN default_currency  CHAR(3)     NOT NULL DEFAULT 'IDR' CHECK (default_currency ~ '^[A-Z]{3}$'),
    ADD COLUMN deleted_at        TIMESTAMPTZ;

-- default_currency is only used as the initial value when creating a new trip.

-- Case-insensitive unique: "Budi@Mail.com" = "budi@mail.com"
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email)) WHERE email IS NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT users_email_needs_password
    CHECK ((email IS NULL) = (password_hash IS NULL));

-- ---------------------------------------------------------------------
-- refresh_tokens
-- Only the HASH (SHA-256) of the token is stored, never the token itself.
-- Rotation: every /auth/refresh revokes the old token and creates a new one.
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   TEXT        NOT NULL UNIQUE,
    device_name  TEXT,                         -- e.g. "Pixel 8", "Chrome on MacBook"
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

-- ---------------------------------------------------------------------
-- password_reset_tokens
-- The "forgot password" link is valid for 30 minutes and can be used once.
-- Only the HASH of the token is stored. After the new password is saved,
-- all of the user's refresh_tokens are revoked (other devices are signed out).
-- ---------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id) WHERE used_at IS NULL;
