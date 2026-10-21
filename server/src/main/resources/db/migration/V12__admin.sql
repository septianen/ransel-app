-- =====================================================================
-- V12: admin app "Karung", stage A (docs/specs/admin-spec.md)
-- users.disabled_at   an admin deactivated the account: sign-in and refresh answer 403 ACCOUNT_DISABLED, data stays
-- credit_ledger.note  the admin's reason for an ADJUSTMENT
-- credit_ledger sign  an ADJUSTMENT may now take credits away (negative delta); the wallet CHECK (balance >= 0) still holds
-- admin_audit_log     append-only record of every admin action
-- =====================================================================
ALTER TABLE users ADD COLUMN disabled_at TIMESTAMPTZ;

ALTER TABLE credit_ledger ADD COLUMN note TEXT;

-- V8's table CHECK ((kind = 'SPEND') = (delta < 0)) has a generated name; find it by its definition.
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'credit_ledger'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%SPEND%' AND pg_get_constraintdef(oid) LIKE '%delta < 0%'
    LOOP
        EXECUTE format('ALTER TABLE credit_ledger DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE credit_ledger ADD CONSTRAINT credit_ledger_kind_sign CHECK (
    (kind = 'SPEND' AND delta < 0)
    OR kind = 'ADJUSTMENT'
    OR (kind NOT IN ('SPEND', 'ADJUSTMENT') AND delta > 0)
);

-- No foreign keys: the log must outlive the accounts it mentions.
CREATE TABLE admin_audit_log (
    id           UUID        PRIMARY KEY,
    admin_id     UUID        NOT NULL,
    admin_email  TEXT        NOT NULL,
    action       TEXT        NOT NULL CHECK (action IN (
                     'CREDITS_ADJUST', 'USER_SIGN_OUT_ALL', 'USER_DISABLE', 'USER_ENABLE', 'PASSWORD_RESET_SEND', 'USERS_EXPORT')),
    target_type  TEXT        NOT NULL CHECK (target_type IN ('USER', 'SETTING', 'CONTENT', 'EXPORT')),
    target_id    TEXT,
    before       JSONB,
    after        JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_admin_audit_target ON admin_audit_log (target_id, created_at DESC);
CREATE INDEX idx_admin_audit_created ON admin_audit_log (created_at DESC);
