-- =====================================================================
-- V13: email verification after sign-up (TRD "Auth and security")
-- users.email_verified_at      null until the link in the sign-up email is opened; Explore and vouchers wait for it
-- email_verification_tokens    like password_reset_tokens: only the hash is stored, valid 24 h, the newest link only
-- Every account that exists now (including seed accounts) counts as verified.
-- =====================================================================
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

UPDATE users SET email_verified_at = created_at WHERE email IS NOT NULL;

CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_verification_user ON email_verification_tokens (user_id) WHERE used_at IS NULL;
