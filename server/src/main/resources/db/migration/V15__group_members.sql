-- =====================================================================
-- V15: group foundation (PRD "Group trip features", TRD "Group trip features")
-- People in a trip are trip members with their own id; a member does not need an account.
--   trip_members          gets `id`; user_id becomes nullable (a member added by name); display_name, phone,
--                         claimed_at, removed_at. Removing or leaving soft-removes (removed_at), so expenses keep
--                         pointing at a row that still has a name. Rejoining clears removed_at (same id).
--   expenses, expense_splits, expense_payers, settlements
--                         point at trip_members.id (…_member_id) instead of users.id.
--   member_claims         log of "Kamu yang mana?" claims: which rows moved, so the OWNER can undo it.
--   comments              on itinerary items and expenses (no chat).
-- The id of an account's own member row is member_id_for(trip, user): the first 16 bytes of
-- SHA-256('<trip>:<user>') shaped like a UUID v5. The app computes the same value (shared memberIdFor), so it
-- can write paid_by_member_id offline. The function exists only for this migration.
-- =====================================================================
CREATE FUNCTION member_id_for(p_trip UUID, p_user UUID) RETURNS UUID
    LANGUAGE sql IMMUTABLE AS
$$
SELECT encode(set_byte(set_byte(h, 6, (get_byte(h, 6) & 15) | 80), 8, (get_byte(h, 8) & 63) | 128), 'hex')::uuid
FROM (SELECT substring(sha256(convert_to(lower(p_trip::text) || ':' || lower(p_user::text), 'UTF8')) FROM 1 FOR 16) AS h) x
$$;

-- ---------------------------------------------------------------------
-- trip_members
-- ---------------------------------------------------------------------
ALTER TABLE trip_members DROP CONSTRAINT trip_members_pkey;
ALTER TABLE trip_members ADD COLUMN id UUID;
UPDATE trip_members SET id = member_id_for(trip_id, user_id);
ALTER TABLE trip_members ALTER COLUMN id SET NOT NULL;
ALTER TABLE trip_members ADD PRIMARY KEY (id);

ALTER TABLE trip_members
    ALTER COLUMN user_id DROP NOT NULL,
    ADD COLUMN display_name         TEXT,
    ADD COLUMN phone                TEXT,
    ADD COLUMN claimed_at           TIMESTAMPTZ,
    ADD COLUMN claimed_by_member_id UUID REFERENCES trip_members (id),
    ADD COLUMN removed_at           TIMESTAMPTZ,
    ADD CONSTRAINT trip_members_has_name CHECK (user_id IS NOT NULL OR display_name IS NOT NULL),
    ADD CONSTRAINT trip_members_guest_is_viewer CHECK (user_id IS NOT NULL OR role = 'VIEWER');

-- One row per account per trip (removed rows included, so a rejoin reuses its row).
CREATE UNIQUE INDEX uq_trip_members_user ON trip_members (trip_id, user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_trip_members_trip ON trip_members (trip_id) WHERE removed_at IS NULL;

-- People who were removed before this migration still appear on expenses: give them a (removed) row.
INSERT INTO trip_members (id, trip_id, user_id, role, joined_at, removed_at)
SELECT DISTINCT member_id_for(x.trip_id, x.user_id), x.trip_id, x.user_id, 'VIEWER', now(), now()
FROM (
    SELECT trip_id, paid_by_user_id AS user_id FROM expenses
    UNION SELECT e.trip_id, s.user_id FROM expense_splits s JOIN expenses e ON e.id = s.expense_id
    UNION SELECT e.trip_id, p.user_id FROM expense_payers p JOIN expenses e ON e.id = p.expense_id
    UNION SELECT trip_id, from_user_id FROM settlements
    UNION SELECT trip_id, to_user_id FROM settlements
) x
WHERE NOT EXISTS (SELECT 1 FROM trip_members m WHERE m.trip_id = x.trip_id AND m.user_id = x.user_id);

-- ---------------------------------------------------------------------
-- Money tables: users.id -> trip_members.id
-- ---------------------------------------------------------------------
ALTER TABLE expenses ADD COLUMN paid_by_member_id UUID;
UPDATE expenses SET paid_by_member_id = member_id_for(trip_id, paid_by_user_id);
ALTER TABLE expenses DROP COLUMN paid_by_user_id;
ALTER TABLE expenses ALTER COLUMN paid_by_member_id SET NOT NULL;
ALTER TABLE expenses ADD FOREIGN KEY (paid_by_member_id) REFERENCES trip_members (id);

ALTER TABLE expense_splits ADD COLUMN member_id UUID;
UPDATE expense_splits s SET member_id = member_id_for(e.trip_id, s.user_id) FROM expenses e WHERE e.id = s.expense_id;
ALTER TABLE expense_splits DROP COLUMN user_id;
ALTER TABLE expense_splits ALTER COLUMN member_id SET NOT NULL;
ALTER TABLE expense_splits ADD PRIMARY KEY (expense_id, member_id);
ALTER TABLE expense_splits ADD FOREIGN KEY (member_id) REFERENCES trip_members (id);
CREATE INDEX idx_expense_splits_member ON expense_splits (member_id);

ALTER TABLE expense_payers ADD COLUMN member_id UUID;
UPDATE expense_payers p SET member_id = member_id_for(e.trip_id, p.user_id) FROM expenses e WHERE e.id = p.expense_id;
ALTER TABLE expense_payers DROP COLUMN user_id;
ALTER TABLE expense_payers ALTER COLUMN member_id SET NOT NULL;
ALTER TABLE expense_payers ADD PRIMARY KEY (expense_id, member_id);
ALTER TABLE expense_payers ADD FOREIGN KEY (member_id) REFERENCES trip_members (id);
CREATE INDEX idx_expense_payers_member ON expense_payers (member_id);

ALTER TABLE settlements ADD COLUMN from_member_id UUID, ADD COLUMN to_member_id UUID;
UPDATE settlements SET from_member_id = member_id_for(trip_id, from_user_id), to_member_id = member_id_for(trip_id, to_user_id);
ALTER TABLE settlements DROP COLUMN from_user_id, DROP COLUMN to_user_id;
ALTER TABLE settlements
    ALTER COLUMN from_member_id SET NOT NULL,
    ALTER COLUMN to_member_id SET NOT NULL,
    ADD FOREIGN KEY (from_member_id) REFERENCES trip_members (id),
    ADD FOREIGN KEY (to_member_id) REFERENCES trip_members (id),
    ADD CONSTRAINT settlements_different_members CHECK (from_member_id <> to_member_id);

DROP FUNCTION member_id_for(UUID, UUID);

-- ---------------------------------------------------------------------
-- member_claims
-- "Kamu yang mana?": the caller's own member takes over a member added by name. `moved` lists the ids of the rows that
-- were rewritten, per table, so the OWNER can undo the claim without touching what was recorded after it.
-- ---------------------------------------------------------------------
CREATE TABLE member_claims (
    id                   UUID        PRIMARY KEY,
    trip_id              UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    guest_member_id      UUID        NOT NULL REFERENCES trip_members (id),
    claimed_by_member_id UUID        NOT NULL REFERENCES trip_members (id),
    moved                JSONB       NOT NULL DEFAULT '{}'::jsonb,
    claimed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    undone_at            TIMESTAMPTZ
);

-- One live claim per member that claims, and a guest is claimed once at a time.
CREATE UNIQUE INDEX uq_member_claims_claimer ON member_claims (trip_id, claimed_by_member_id) WHERE undone_at IS NULL;
CREATE UNIQUE INDEX uq_member_claims_guest ON member_claims (guest_member_id) WHERE undone_at IS NULL;

-- ---------------------------------------------------------------------
-- comments (no chat): on itinerary items and expenses. Online only, not part of /sync.
-- target_id is not a foreign key: it points at itinerary_items or expenses depending on target_kind.
-- ---------------------------------------------------------------------
CREATE TABLE comments (
    id                UUID        PRIMARY KEY,
    trip_id           UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    target_kind       TEXT        NOT NULL CHECK (target_kind IN ('ITINERARY_ITEM', 'EXPENSE')),
    target_id         UUID        NOT NULL,
    author_member_id  UUID        NOT NULL REFERENCES trip_members (id),
    body              TEXT        NOT NULL CHECK (length(body) BETWEEN 1 AND 1000),
    mentions          JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at         TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX idx_comments_target ON comments (trip_id, target_kind, target_id, created_at) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- notifications: two new types (member claimed, comment mention)
-- ---------------------------------------------------------------------
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'MEMBER_JOINED', 'MEMBER_REMOVED', 'ROLE_CHANGED', 'EXPENSE_ADDED', 'SETTLEMENT_MARKED',
    'VOTE_REQUESTED', 'VOTE_RESULT', 'PACKING_REMINDER', 'ACTIVITY_REMINDER',
    'MEMBER_CLAIMED', 'COMMENT_MENTION'));
