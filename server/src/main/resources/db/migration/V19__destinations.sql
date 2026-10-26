-- =====================================================================
-- V19: destinations picked from suggestions (PRD "Destination suggestions and current location", TRD "Places")
--   trips                     where the destination is when it was picked from the suggestions; `destination` keeps the
--                             name. The coordinates are the place's own, never the user's location.
--   place_autocomplete_cache  suggestions per normalised query (and rounded bias point), 24 hours.
--   place_details_cache       coordinates, viewport and country per Google place id, 30 days.
-- Postgres instead of Redis (owner decision 26 Oct 2026: no new container). The fallback region list is in the shared
-- module (domain/IndonesianRegions.kt), so no pg_trgm table is needed.
-- =====================================================================
ALTER TABLE trips
    ADD COLUMN dest_place_id TEXT,
    ADD COLUMN dest_lat      DOUBLE PRECISION CHECK (dest_lat BETWEEN -90 AND 90),
    ADD COLUMN dest_lng      DOUBLE PRECISION CHECK (dest_lng BETWEEN -180 AND 180),
    ADD COLUMN dest_viewport JSONB;

-- "Populer untuk trip grup" groups recent trips by place
CREATE INDEX idx_trips_dest_place ON trips (dest_place_id, created_at) WHERE dest_place_id IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE place_autocomplete_cache (
    query_key    TEXT        PRIMARY KEY,
    suggestions  JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE place_details_cache (
    place_id    TEXT        PRIMARY KEY,
    place       JSONB       NOT NULL,
    fetched_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
