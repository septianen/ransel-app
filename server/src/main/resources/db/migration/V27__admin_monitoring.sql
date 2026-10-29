-- =====================================================================
-- V27: admin app "Karung", stage B: monitoring data (docs/specs/admin-spec.md "Monitoring data")
-- users.last_seen_at     an authenticated request, written at most once an hour per user
-- user_active_days       the WIB days a user made one, for daily and 7-day active users over time (kept 400 days)
-- ai_search_log          one row per Explore or poll search: parameters only, never prompt text (kept 180 days)
-- provider_usage         calls to paid outside services per WIB day, with their estimated cost (kept 400 days)
-- error_counts           error responses per hour, code and route template, never a path with ids (kept 90 days)
-- error_count_users      who got them, so a range can count distinct users (kept 90 days)
-- No foreign keys: the logs outlive the accounts and trips they mention.
-- =====================================================================
ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMPTZ;
CREATE INDEX idx_users_last_seen ON users (last_seen_at);

CREATE TABLE user_active_days (
    day      DATE NOT NULL,
    user_id  UUID NOT NULL,
    PRIMARY KEY (day, user_id)
);

CREATE TABLE ai_search_log (
    id                  UUID        PRIMARY KEY,
    user_id             UUID        NOT NULL,
    trip_id             UUID,
    -- EXPLORE = the swipe search; POLL = Jelajah from a stay or food poll
    kind                TEXT        NOT NULL CHECK (kind IN ('EXPLORE', 'POLL')),
    destination         TEXT        NOT NULL,
    price_level         TEXT,
    interests           TEXT[]      NOT NULL DEFAULT '{}',
    new_search          BOOLEAN     NOT NULL,
    source              TEXT        NOT NULL CHECK (source IN ('NEW', 'CACHE', 'FAILED', 'NO_CREDITS')),
    cards               INT         NOT NULL DEFAULT 0,
    -- the cards' Google place ids: "saved" counts the likes among them
    place_ids           TEXT[]      NOT NULL DEFAULT '{}',
    latency_ms          INT         NOT NULL,
    credits_charged     INT         NOT NULL DEFAULT 0,
    llm_cost_micro_usd  BIGINT      NOT NULL DEFAULT 0,
    places_cost_micro_usd BIGINT    NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_ai_search_log_created ON ai_search_log (created_at DESC);
CREATE INDEX idx_ai_search_log_user ON ai_search_log (user_id, created_at DESC);

CREATE TABLE provider_usage (
    day             DATE   NOT NULL,
    call            TEXT   NOT NULL,
    service         TEXT   NOT NULL CHECK (service IN ('LLM', 'PLACES')),
    calls           BIGINT NOT NULL,
    cost_micro_usd  BIGINT NOT NULL,
    PRIMARY KEY (day, call)
);

CREATE TABLE error_counts (
    hour     TIMESTAMPTZ NOT NULL,
    code     TEXT        NOT NULL,
    -- "POST /recommendations", "GET /trips/{id}/expenses"; "-" when no route matched
    route    TEXT        NOT NULL,
    status   INT         NOT NULL,
    count    BIGINT      NOT NULL,
    last_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (hour, code, route)
);
CREATE INDEX idx_error_counts_code ON error_counts (code, hour DESC);

CREATE TABLE error_count_users (
    hour     TIMESTAMPTZ NOT NULL,
    code     TEXT        NOT NULL,
    route    TEXT        NOT NULL,
    user_id  UUID        NOT NULL,
    PRIMARY KEY (hour, code, route, user_id)
);
