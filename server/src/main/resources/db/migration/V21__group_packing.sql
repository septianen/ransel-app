-- =====================================================================
-- V21: group packing (PRD "Packing list from the trip owner", P1 "Shared packing items")
--   packing_items           the trip-wide items are now the "Bersama" list; assignee = who brings it ("Saya bawa").
--                           Owner decision 26 Oct 2026: the items trips already have stay here as Bersama.
--   personal_items          each member's own "Pribadi" list; lines from the owner's list carry source_item_id and
--                           required. Only the owner of a line sees it.
--   member_lists            "Daftar bawaan anggota": one per trip, versioned so two editors do not overwrite each other.
--   member_list_items       its lines.
--   member_list_recipients  who received it when it was not sent to everyone.
-- =====================================================================
ALTER TABLE packing_items ADD COLUMN assignee_member_id UUID REFERENCES trip_members (id);

CREATE TABLE member_lists (
    trip_id               UUID        PRIMARY KEY REFERENCES trips (id) ON DELETE CASCADE,
    name                  TEXT        NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 60),
    version               INTEGER     NOT NULL DEFAULT 1,
    sent_to_all           BOOLEAN     NOT NULL DEFAULT FALSE,
    sent_at               TIMESTAMPTZ,
    created_by_member_id  UUID        NOT NULL REFERENCES trip_members (id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_member_lists_updated_at
    BEFORE UPDATE ON member_lists
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE member_list_items (
    id          UUID             PRIMARY KEY,
    trip_id     UUID             NOT NULL REFERENCES member_lists (trip_id) ON DELETE CASCADE,
    category    TEXT             NOT NULL,
    name        TEXT             NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 100),
    quantity    INTEGER          NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    required    BOOLEAN          NOT NULL DEFAULT FALSE,
    sort_order  DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE INDEX idx_member_list_items_trip ON member_list_items (trip_id);

CREATE TABLE member_list_recipients (
    trip_id    UUID NOT NULL REFERENCES member_lists (trip_id) ON DELETE CASCADE,
    member_id  UUID NOT NULL REFERENCES trip_members (id),
    PRIMARY KEY (trip_id, member_id)
);

CREATE TABLE personal_items (
    id               UUID             PRIMARY KEY,
    trip_id          UUID             NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    owner_member_id  UUID             NOT NULL REFERENCES trip_members (id),
    category         TEXT             NOT NULL,
    name             TEXT             NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 100),
    quantity         INTEGER          NOT NULL CHECK (quantity > 0),
    is_packed        BOOLEAN          NOT NULL DEFAULT FALSE,
    sort_order       DOUBLE PRECISION NOT NULL DEFAULT 0,
    -- the member list line it came from; no foreign key: a removed line soft-deletes its copies
    source_item_id   UUID,
    required         BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);

CREATE INDEX idx_personal_items_owner ON personal_items (owner_member_id, updated_at);
CREATE INDEX idx_personal_items_source ON personal_items (source_item_id) WHERE source_item_id IS NOT NULL;
CREATE UNIQUE INDEX idx_personal_items_one_copy ON personal_items (owner_member_id, source_item_id)
    WHERE source_item_id IS NOT NULL AND deleted_at IS NULL;

CREATE TRIGGER trg_personal_items_updated_at
    BEFORE UPDATE ON personal_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'MEMBER_JOINED', 'MEMBER_REMOVED', 'ROLE_CHANGED', 'EXPENSE_ADDED', 'SETTLEMENT_MARKED',
    'VOTE_REQUESTED', 'VOTE_RESULT', 'PACKING_REMINDER', 'ACTIVITY_REMINDER',
    'MEMBER_CLAIMED', 'COMMENT_MENTION', 'POLL_REQUESTED', 'POLL_RESULT', 'PACKING_LIST'));
