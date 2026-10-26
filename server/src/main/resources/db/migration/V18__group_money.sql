-- =====================================================================
-- V18: group money (PRD "Group kitty", "Getting paid", "Receipt photo"; shared/domain/GroupMoney.kt)
--   kitty            one per trip: the agreed contribution per person and the treasurer who holds the money.
--   kitty_deposits   money a member put in, received by the treasurer of that moment. In balances a deposit counts as
--                    the member paying the treasurer, and a kitty expense as paid by the treasurer.
--   expenses         paid_from_kitty, is_estimate (a planned cost, e.g. the stay chosen in a poll; not in balances).
--   expense_items    receipt lines for the "per item" split; the splits stay the source of truth for balances.
--   settlements      status: CLAIMED_PAID ("Saya sudah bayar", waits for the receiver) or CONFIRMED (counts in balances).
--   payment_methods  bank accounts and e-wallets of an account, shown only to trip members who owe that person.
-- Kitty amounts are in the trip's base currency (owner decision 26 Oct 2026).
-- The TRD of 24 Oct put these in V3/V5; V1–V17 are live, so they come here.
-- =====================================================================
CREATE TABLE kitty (
    trip_id                  UUID        PRIMARY KEY REFERENCES trips (id) ON DELETE CASCADE,
    amount_per_member_minor  BIGINT      NOT NULL CHECK (amount_per_member_minor > 0),
    treasurer_member_id      UUID        NOT NULL REFERENCES trip_members (id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at               TIMESTAMPTZ
);

CREATE INDEX idx_kitty_updated ON kitty (updated_at);

CREATE TRIGGER trg_kitty_updated_at
    BEFORE UPDATE ON kitty
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE kitty_deposits (
    id                     UUID        PRIMARY KEY,
    trip_id                UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    member_id              UUID        NOT NULL REFERENCES trip_members (id),
    amount_minor           BIGINT      NOT NULL CHECK (amount_minor > 0),
    received_by_member_id  UUID        NOT NULL REFERENCES trip_members (id),
    received_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ
);

CREATE INDEX idx_kitty_deposits_trip ON kitty_deposits (trip_id, received_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_kitty_deposits_trip_updated ON kitty_deposits (trip_id, updated_at);

CREATE TRIGGER trg_kitty_deposits_updated_at
    BEFORE UPDATE ON kitty_deposits
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE expenses
    ADD COLUMN paid_from_kitty BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_estimate     BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE expense_items (
    expense_id    UUID    NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    sort_order    INTEGER NOT NULL,
    name          TEXT    NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
    amount_minor  BIGINT  NOT NULL CHECK (amount_minor >= 0),
    -- trip_members ids sharing the line; empty = tax or service spread in proportion
    member_ids    JSONB   NOT NULL DEFAULT '[]',
    PRIMARY KEY (expense_id, sort_order)
);

ALTER TABLE settlements
    ADD COLUMN status     TEXT NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CLAIMED_PAID', 'CONFIRMED')),
    ADD COLUMN claimed_at TIMESTAMPTZ;

CREATE TABLE payment_methods (
    id              UUID        PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind            TEXT        NOT NULL CHECK (kind IN ('BANK', 'EWALLET')),
    provider        TEXT        NOT NULL CHECK (length(provider) BETWEEN 1 AND 40),
    account_number  TEXT        NOT NULL CHECK (length(account_number) BETWEEN 4 AND 40),
    holder_name     TEXT        NOT NULL CHECK (length(holder_name) BETWEEN 1 AND 80),
    is_primary      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_payment_methods_user ON payment_methods (user_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_payment_methods_updated_at
    BEFORE UPDATE ON payment_methods
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- reading a receipt photo is a new credit action; V8's column CHECK is found by its definition, like V12 did
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'credit_ledger'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%NEXT_BATCH%'
    LOOP
        EXECUTE format('ALTER TABLE credit_ledger DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE credit_ledger ADD CONSTRAINT credit_ledger_action_check
    CHECK (action IN ('SEARCH', 'NEXT_BATCH', 'PLACE_DETAIL', 'RECEIPT'));
