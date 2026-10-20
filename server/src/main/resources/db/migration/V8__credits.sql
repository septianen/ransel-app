-- =====================================================================
-- V8: Explore credits (replaces the fixed 10 searches per day; ai_usage stays for one release)
-- wallet            one row per user, created lazily with the signup grant (see credit_ledger.idempotency_key)
-- credit_daily      free allowance used per WIB day; the allowance itself comes from config, not from the table
-- credit_ledger     append-only audit trail; delta < 0 only for SPEND. A SPEND that straddles the free allowance and the
--                   wallet is two rows (FREE_DAILY, WALLET). balance_after = wallet balance after the row.
-- credit_vouchers   top-up codes made by hand until a payment provider exists; one redemption per user per code
-- =====================================================================
CREATE TABLE credit_wallets (
    user_id     UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    balance     INTEGER     NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE credit_daily (
    user_id     UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    usage_date  DATE    NOT NULL,
    free_used   INTEGER NOT NULL DEFAULT 0 CHECK (free_used >= 0),
    PRIMARY KEY (user_id, usage_date)
);

CREATE TABLE credit_ledger (
    id               UUID        PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind             TEXT        NOT NULL CHECK (kind IN ('SIGNUP_GRANT', 'SPEND', 'VOUCHER', 'TOPUP', 'REFUND', 'ADJUSTMENT')),
    delta            INTEGER     NOT NULL CHECK (delta <> 0),
    source           TEXT        CHECK (source IN ('FREE_DAILY', 'WALLET')),
    action           TEXT        CHECK (action IN ('SEARCH', 'NEXT_BATCH', 'PLACE_DETAIL')),
    ref              TEXT,
    idempotency_key  TEXT        UNIQUE,
    balance_after    INTEGER     NOT NULL,
    -- clock_timestamp(), not now(): the signup grant and the first charge can share one transaction, and the history is ordered by this
    created_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK ((kind = 'SPEND') = (delta < 0))
);

CREATE INDEX idx_credit_ledger_user ON credit_ledger (user_id, created_at DESC);

CREATE TABLE credit_vouchers (
    code        TEXT        PRIMARY KEY,
    credits     INTEGER     NOT NULL CHECK (credits > 0),
    max_uses    INTEGER     NOT NULL DEFAULT 1 CHECK (max_uses > 0),
    used_count  INTEGER     NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    expires_at  TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE credit_voucher_redemptions (
    code         TEXT        NOT NULL REFERENCES credit_vouchers (code),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    redeemed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (code, user_id)
);

CREATE TRIGGER trg_credit_wallets_updated_at
    BEFORE UPDATE ON credit_wallets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
