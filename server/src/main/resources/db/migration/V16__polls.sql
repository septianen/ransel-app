-- =====================================================================
-- V16: polls (PRD "Group tab, polling and comments", shared/domain/Polls.kt)
--   polls          one question for the group; OPEN until closes_at, then CLOSED or TIED, DECIDED once the creator
--                  confirmed (or dismissed) it. The status is settled when the poll is next read or by the minute job.
--   poll_options   the choices. `version` goes up when what people voted on changes; votes carry the version they saw.
--   poll_votes     one row per member per chosen option. The API never returns who voted for what on an anonymous poll.
-- Comments may now hang on an option, and two notification types are added.
-- =====================================================================
CREATE TABLE polls (
    id                    UUID        PRIMARY KEY,
    trip_id               UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    kind                  TEXT        NOT NULL CHECK (kind IN ('DATE', 'PLACE', 'STAY', 'FOOD', 'BUDGET', 'DESTINATION', 'TRANSPORT', 'CUSTOM')),
    question              TEXT        NOT NULL CHECK (length(trim(question)) BETWEEN 1 AND 140),
    created_by_member_id  UUID        NOT NULL REFERENCES trip_members (id),
    closes_at             TIMESTAMPTZ NOT NULL,
    multi_choice          BOOLEAN     NOT NULL DEFAULT FALSE,
    is_anonymous          BOOLEAN     NOT NULL DEFAULT FALSE,
    -- set by the first vote: from then on the anonymous switch cannot change (even if that vote is taken back)
    anonymity_locked      BOOLEAN     NOT NULL DEFAULT FALSE,
    apply_winner          BOOLEAN     NOT NULL DEFAULT TRUE,
    day_date              DATE,
    start_time            TIME,
    status                TEXT        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'TIED', 'CLOSED', 'DECIDED')),
    decided_option_id     UUID,
    decided_at            TIMESTAMPTZ,
    tied_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,
    -- budget polls are always anonymous
    CONSTRAINT polls_budget_is_anonymous CHECK (kind <> 'BUDGET' OR is_anonymous)
);

CREATE INDEX idx_polls_trip ON polls (trip_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_polls_due ON polls (closes_at) WHERE status = 'OPEN' AND deleted_at IS NULL;
CREATE INDEX idx_polls_tied ON polls (tied_at) WHERE status = 'TIED' AND deleted_at IS NULL;

CREATE TRIGGER trg_polls_updated_at
    BEFORE UPDATE ON polls
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE poll_options (
    id          UUID        PRIMARY KEY,
    poll_id     UUID        NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    label       TEXT        NOT NULL CHECK (length(trim(label)) BETWEEN 1 AND 100),
    start_date  DATE,
    end_date    DATE,
    place_id    TEXT,
    url         TEXT,
    image_url   TEXT,
    price_minor BIGINT      CHECK (price_minor IS NULL OR price_minor >= 0),
    price_unit  TEXT        CHECK (price_unit IS NULL OR price_unit IN ('NIGHT', 'PERSON', 'TOTAL')),
    min_minor   BIGINT      CHECK (min_minor IS NULL OR min_minor >= 0),
    max_minor   BIGINT,
    version     INTEGER     NOT NULL DEFAULT 1,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_poll_options_poll ON poll_options (poll_id, sort_order) WHERE deleted_at IS NULL;

ALTER TABLE polls ADD FOREIGN KEY (decided_option_id) REFERENCES poll_options (id);

CREATE TABLE poll_votes (
    option_id       UUID        NOT NULL REFERENCES poll_options (id) ON DELETE CASCADE,
    poll_id         UUID        NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    member_id       UUID        NOT NULL REFERENCES trip_members (id),
    option_version  INTEGER     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (option_id, member_id)
);

CREATE INDEX idx_poll_votes_poll ON poll_votes (poll_id, member_id);

-- comments on an option
ALTER TABLE comments DROP CONSTRAINT comments_target_kind_check;
ALTER TABLE comments ADD CONSTRAINT comments_target_kind_check CHECK (target_kind IN ('ITINERARY_ITEM', 'EXPENSE', 'POLL_OPTION'));

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'MEMBER_JOINED', 'MEMBER_REMOVED', 'ROLE_CHANGED', 'EXPENSE_ADDED', 'SETTLEMENT_MARKED',
    'VOTE_REQUESTED', 'VOTE_RESULT', 'PACKING_REMINDER', 'ACTIVITY_REMINDER',
    'MEMBER_CLAIMED', 'COMMENT_MENTION', 'POLL_REQUESTED', 'POLL_RESULT'));
