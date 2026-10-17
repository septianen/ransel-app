-- =====================================================================
-- V6: place detail text (docs/specs/voting-spec.md, design "DetailTempat")
-- The LLM writes about/tips/hours estimate in the same call as the search; each place's text is kept here per
-- Google place id and language so the itinerary can open it again later. Expires after 30 days; an expired
-- row is rebuilt by one small LLM call when someone opens the place again.
-- Only our own LLM text and the place id/name/address/coordinates the app already shows: no photos, no Google
-- ratings. (A place id may be kept permanently; coordinates only for 30 days, hence the expiry.)
-- =====================================================================
CREATE TABLE place_detail_cache (
    place_id    TEXT        NOT NULL,
    lang        TEXT        NOT NULL DEFAULT 'id',
    response    JSONB       NOT NULL,                 -- CachedDetail
    model       TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (place_id, lang)
);

CREATE INDEX idx_place_detail_cache_expires ON place_detail_cache (expires_at);
