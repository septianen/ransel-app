-- =====================================================================
-- V4: Phase 5 — AI recommendations (cache + swipe results)
-- =====================================================================

-- ---------------------------------------------------------------------
-- recommendation_cache
-- query_hash = SHA-256 of destination + interests + budget + style (normalized).
-- Stores only validated LLM results (name, reason, placeId, lat/lng).
-- Photos are NOT stored: Google photo names can expire, so they are fetched when the card shows.
-- ---------------------------------------------------------------------
CREATE TABLE recommendation_cache (
    query_hash    TEXT PRIMARY KEY,
    destination   TEXT        NOT NULL,          -- for debugging / statistics
    response      JSONB       NOT NULL,
    model         TEXT        NOT NULL,          -- e.g. 'deepseek-flash'
    hit_count     INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL
);

-- For the cleanup job: DELETE FROM recommendation_cache WHERE expires_at < now()
CREATE INDEX idx_recommendation_cache_expires ON recommendation_cache (expires_at);

-- ---------------------------------------------------------------------
-- recommendation_feedback
-- One row per swiped card within one trip.
-- LIKE  -> also creates itinerary_items (source = 'AI', day_date = NULL)
-- SKIP  -> this place is not offered again for the same trip
-- Both are sent to the LLM in the next request as a taste signal.
-- ---------------------------------------------------------------------
CREATE TABLE recommendation_feedback (
    trip_id     UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    place_id    TEXT        NOT NULL,
    place_name  TEXT        NOT NULL,
    category    TEXT        NOT NULL
                CHECK (category IN ('ATTRACTION', 'FOOD', 'TRANSPORT', 'LODGING', 'SHOPPING', 'OTHER')),
    action      TEXT        NOT NULL CHECK (action IN ('LIKE', 'SKIP')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, user_id, place_id)     -- undo / swipe again = UPSERT
);

-- Link itinerary items from swipes to their placeId to avoid duplicates
CREATE UNIQUE INDEX uq_itinerary_trip_place_ai
    ON itinerary_items (trip_id, place_id)
    WHERE source = 'AI' AND place_id IS NOT NULL AND deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- ai_usage
-- Explore quota: 10 searches per user per day, reset 00:00 (WIB).
-- The server increments count on every successful POST /recommendations;
-- if count is already 10 -> 429 AI_QUOTA_EXCEEDED. Explore requires sign-in.
-- ---------------------------------------------------------------------
CREATE TABLE ai_usage (
    user_id     UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    usage_date  DATE    NOT NULL,
    count       INTEGER NOT NULL DEFAULT 0 CHECK (count >= 0),
    PRIMARY KEY (user_id, usage_date)
);
