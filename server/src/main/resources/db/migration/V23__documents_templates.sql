-- =====================================================================
-- V23: trip documents and trip templates (PRD P2 "Trip documents", "Trip recap")
--   trip_documents  PDF or image files (on the VPS disk, DOCUMENTS_DIR/<trip>/<id>) or links. Owner decisions
--                   26 Oct 2026: disk instead of object storage, 20 MB per file, 200 MB per trip, kept while the trip
--                   exists (files of deleted trips are removed by the daily job).
--   trip_templates  a user's itinerary-by-day-number and shared packing list, saved from a trip.
-- =====================================================================
CREATE TABLE trip_documents (
    id                     UUID        PRIMARY KEY,
    trip_id                UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    title                  TEXT        NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 80),
    category               TEXT        NOT NULL CHECK (category IN ('TRAVEL', 'STAY', 'ACTIVITY', 'OTHER')),
    kind                   TEXT        NOT NULL CHECK (kind IN ('FILE', 'LINK')),
    url                    TEXT,
    file_name              TEXT,
    mime                   TEXT,
    size_bytes             BIGINT,
    itinerary_item_id      UUID        REFERENCES itinerary_items (id) ON DELETE SET NULL,
    uploaded_by_member_id  UUID        NOT NULL REFERENCES trip_members (id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    file_removed_at        TIMESTAMPTZ,
    CHECK ((kind = 'LINK' AND url IS NOT NULL) OR (kind = 'FILE' AND size_bytes IS NOT NULL AND mime IS NOT NULL))
);
CREATE INDEX trip_documents_trip ON trip_documents (trip_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_trip_documents_updated_at
    BEFORE UPDATE ON trip_documents
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE trip_templates (
    id           UUID        PRIMARY KEY,
    owner_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         TEXT        NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 60),
    day_count    INTEGER     NOT NULL CHECK (day_count >= 1),
    destination  TEXT        NOT NULL DEFAULT '',
    items        JSONB       NOT NULL DEFAULT '[]',
    packing      JSONB       NOT NULL DEFAULT '[]',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX trip_templates_owner ON trip_templates (owner_id);

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'MEMBER_JOINED', 'MEMBER_REMOVED', 'ROLE_CHANGED', 'EXPENSE_ADDED', 'SETTLEMENT_MARKED',
    'VOTE_REQUESTED', 'VOTE_RESULT', 'PACKING_REMINDER', 'ACTIVITY_REMINDER',
    'MEMBER_CLAIMED', 'COMMENT_MENTION', 'POLL_REQUESTED', 'POLL_RESULT', 'PACKING_LIST',
    'DOCUMENT_ADDED', 'RECAP_READY'));
