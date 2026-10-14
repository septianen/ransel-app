-- =====================================================================
-- V3: Phase 4 — trip members, invites and split bill
-- =====================================================================

-- ---------------------------------------------------------------------
-- trip_members
-- From this phase on, access is checked against this table, not trips.owner_id.
-- owner_id stays as the marker of who created the trip.
-- ---------------------------------------------------------------------
CREATE TABLE trip_members (
    trip_id     UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role        TEXT        NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, user_id)
);

-- "Which trips can I access?" -> read from the user side
CREATE INDEX idx_trip_members_user ON trip_members (user_id);

-- Exactly one OWNER per trip
CREATE UNIQUE INDEX uq_trip_single_owner ON trip_members (trip_id) WHERE role = 'OWNER';

CREATE TRIGGER trg_trip_members_updated_at
    BEFORE UPDATE ON trip_members
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Backfill: the owner of every existing trip becomes its OWNER member
INSERT INTO trip_members (trip_id, user_id, role)
SELECT id, owner_id, 'OWNER' FROM trips;

-- ---------------------------------------------------------------------
-- trip_invites
-- code: e.g. BALI-7K2Q, shared via the link ransel.app/j/<code>.
-- Rejected when: revoked_at set -> INVITE_REVOKED, past expires_at -> INVITE_EXPIRED,
-- used_count = max_uses -> INVITE_FULL. Accept = INSERT trip_members + used_count + 1 in one transaction.
-- ---------------------------------------------------------------------
CREATE TABLE trip_invites (
    code        TEXT PRIMARY KEY CHECK (length(code) BETWEEN 6 AND 32),
    trip_id     UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    role        TEXT        NOT NULL DEFAULT 'EDITOR' CHECK (role IN ('EDITOR', 'VIEWER')),
    created_by  UUID        NOT NULL REFERENCES users (id),
    max_uses    INTEGER     CHECK (max_uses IS NULL OR max_uses > 0),
    used_count  INTEGER     NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT invites_usage_limit CHECK (max_uses IS NULL OR used_count <= max_uses)
);

CREATE INDEX idx_trip_invites_trip ON trip_invites (trip_id);

-- ---------------------------------------------------------------------
-- expense_splits
-- How one expense is shared between people, in that expense's currency.
-- Rule (checked in the service, not the DB): SUM(share_minor) = expenses.amount_minor.
-- Synced as part of the expense: when the expense changes, its splits are replaced as a whole.
-- An expense without split rows is fully covered by paid_by_user_id (solo case).
-- ---------------------------------------------------------------------
CREATE TABLE expense_splits (
    expense_id   UUID   NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    user_id      UUID   NOT NULL REFERENCES users (id),
    share_minor  BIGINT NOT NULL CHECK (share_minor >= 0),
    PRIMARY KEY (expense_id, user_id)
);

CREATE INDEX idx_expense_splits_user ON expense_splits (user_id);

-- ---------------------------------------------------------------------
-- settlements
-- The "Tandai lunas" / Mark as settled button on the Balances screen. In the trip base_currency.
-- User balance = paid - own share + settlements they paid - settlements they received.
-- ---------------------------------------------------------------------
CREATE TABLE settlements (
    id            UUID PRIMARY KEY,
    trip_id       UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    from_user_id  UUID        NOT NULL REFERENCES users (id),
    to_user_id    UUID        NOT NULL REFERENCES users (id),
    amount_minor  BIGINT      NOT NULL CHECK (amount_minor > 0),
    settled_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT settlements_different_users CHECK (from_user_id <> to_user_id)
);

CREATE INDEX idx_settlements_trip ON settlements (trip_id, settled_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_settlements_trip_updated ON settlements (trip_id, updated_at);

CREATE TRIGGER trg_settlements_updated_at
    BEFORE UPDATE ON settlements
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- packing_templates: the "Bagikan ke anggota trip" / Share with trip members option in Save as template.
-- shared_trip_id set = every member of that trip can use the template.
-- ---------------------------------------------------------------------
ALTER TABLE packing_templates
    ADD COLUMN shared_trip_id UUID REFERENCES trips (id) ON DELETE SET NULL;

CREATE INDEX idx_templates_shared_trip ON packing_templates (shared_trip_id) WHERE shared_trip_id IS NOT NULL;
