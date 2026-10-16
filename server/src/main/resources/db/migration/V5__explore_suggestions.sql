-- =====================================================================
-- V5: Explore per trip — destination interests cache (docs/specs/AI-Recommendation-Spec.md)
-- One LLM answer per destination + travel month + language, shared by every user; expires after 30 days.
-- price_low_max_usd / price_mid_max_usd: typical spend per person per day (USD) at the edge between
-- $ and $$ and between $$ and $$$, used to preselect the price level from a trip budget.
-- =====================================================================
CREATE TABLE explore_suggestions_cache (
    destination        TEXT          NOT NULL,          -- normalized: trimmed, lower case, single spaces
    month              CHAR(7)       NOT NULL CHECK (month ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    language           TEXT          NOT NULL,          -- 'id' or 'en'
    interests          JSONB         NOT NULL,          -- ["Sakura", ...]
    price_low_max_usd  NUMERIC(10,2) NOT NULL,
    price_mid_max_usd  NUMERIC(10,2) NOT NULL,
    model              TEXT          NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (destination, month, language)
);

CREATE INDEX idx_explore_suggestions_expires ON explore_suggestions_cache (expires_at);
