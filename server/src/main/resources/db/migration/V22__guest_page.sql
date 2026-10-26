-- =====================================================================
-- V22: sharing and the guest page (PRD "Sharing to WhatsApp")
--   polls.share_code  the poll's guest link (https://<web>/p/<code>); null = never shared or turned off.
--   guest_sessions    a browser that picked a member without an account. Only a hash of the token is stored; one
--                     browser per member (the OWNER releases it by deleting the row).
-- Owner decisions 26 Oct 2026: a written name becomes a member without an account, one browser per name, an IP rate
-- limit and nothing more (no OTP, no captcha).
-- =====================================================================
ALTER TABLE polls ADD COLUMN share_code TEXT;
CREATE UNIQUE INDEX polls_share_code ON polls (share_code) WHERE share_code IS NOT NULL;

CREATE TABLE guest_sessions (
    token_hash    TEXT        PRIMARY KEY,
    trip_id       UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    member_id     UUID        NOT NULL REFERENCES trip_members (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX guest_sessions_member ON guest_sessions (member_id);
