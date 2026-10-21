-- =====================================================================
-- V11: itinerary voting (docs/specs/voting-spec.md)
-- suggestion_rounds        one AI search on a trip with 2+ members; voting open until closes_at (created + 24 h),
--                          gone at expires_at (created + 7 d) when nobody decided
-- suggestion_round_places  the cards of a round with a snapshot of the search result (the RecommendationItem JSON),
--                          so every voter sees the same text without the requester's device
-- suggestion_votes         one LIKE / SKIP per member per card; written by the server only
-- chosen                   the OWNER's decision: the cards that went into the itinerary
-- =====================================================================
CREATE TABLE suggestion_rounds (
    id           UUID        PRIMARY KEY,
    trip_id      UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    created_by   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    closes_at    TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    decided_at   TIMESTAMPTZ,
    decided_by   UUID        REFERENCES users (id) ON DELETE SET NULL,
    CHECK (closes_at > created_at AND expires_at >= closes_at)
);

CREATE INDEX idx_suggestion_rounds_trip ON suggestion_rounds (trip_id, created_at DESC);
CREATE INDEX idx_suggestion_rounds_closing ON suggestion_rounds (closes_at) WHERE decided_at IS NULL;

CREATE TABLE suggestion_round_places (
    round_id    UUID     NOT NULL REFERENCES suggestion_rounds (id) ON DELETE CASCADE,
    place_id    TEXT     NOT NULL,
    category    TEXT     NOT NULL CHECK (category IN ('ATTRACTION', 'FOOD', 'TRANSPORT', 'LODGING', 'SHOPPING', 'OTHER')),
    sort_order  INTEGER  NOT NULL,
    snapshot    JSONB    NOT NULL,
    chosen      BOOLEAN  NOT NULL DEFAULT FALSE,
    PRIMARY KEY (round_id, place_id)
);

CREATE TABLE suggestion_votes (
    round_id    UUID        NOT NULL,
    place_id    TEXT        NOT NULL,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    vote        TEXT        NOT NULL CHECK (vote IN ('LIKE', 'SKIP')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (round_id, place_id, user_id),
    FOREIGN KEY (round_id, place_id) REFERENCES suggestion_round_places (round_id, place_id) ON DELETE CASCADE
);

CREATE INDEX idx_suggestion_votes_user ON suggestion_votes (round_id, user_id);
