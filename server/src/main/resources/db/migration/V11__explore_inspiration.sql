-- Explore Inspirasi (PRD v0.8, owner approved 21 Nov 2026): an AI interview that ends in activity type + region cards.
-- Sessions live 24 h for resume and 90 days for the pricing log; saved ideas are per account (max 30, the 31st removes the
-- oldest with an undo). Enum columns hold the constant names of shared InspirationStatus, InspirationSlot, ... .

ALTER TABLE credit_ledger DROP CONSTRAINT credit_ledger_action_check;
ALTER TABLE credit_ledger ADD CONSTRAINT credit_ledger_action_check
    CHECK (action IS NULL OR action IN ('SEARCH', 'NEXT_BATCH', 'PLACE_DETAIL', 'RECEIPT', 'INSPIRATION'));

CREATE TABLE inspiration_sessions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status text NOT NULL CONSTRAINT inspiration_sessions_status_check
        CHECK (status IN ('ACTIVE', 'READY', 'CHARGED', 'FAILED', 'EXPIRED')),
    language text NOT NULL CONSTRAINT inspiration_sessions_language_check CHECK (language IN ('id', 'en')),
    -- interview state (answers, slots done, facts, current question) as the server's own JSON shape
    state jsonb NOT NULL,
    -- the cards; filled once built, shown to the app only after the charge
    result jsonb,
    ask_again_used integer NOT NULL DEFAULT 0,
    chip_edits_used integer NOT NULL DEFAULT 0,
    charged_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL
);
CREATE INDEX inspiration_sessions_user_created ON inspiration_sessions (user_id, created_at);
CREATE INDEX inspiration_sessions_created ON inspiration_sessions (created_at);

-- One row per AI step, for the pricing dashboard and quality checks. Kept 90 days; free text is scrubbed of phone
-- numbers and emails before it is written and is never shown in a dashboard.
CREATE TABLE inspiration_session_log (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL,
    user_id uuid NOT NULL,
    step integer NOT NULL,
    kind text NOT NULL CONSTRAINT inspiration_session_log_kind_check
        CHECK (kind IN ('QUESTION', 'CARDS', 'ASK_AGAIN', 'CHIP_EDIT', 'REFRESH')),
    slot text,
    source text CONSTRAINT inspiration_session_log_source_check CHECK (source IS NULL OR source IN ('LLM', 'FALLBACK')),
    outcome text NOT NULL CONSTRAINT inspiration_session_log_outcome_check CHECK (outcome IN ('OK', 'FALLBACK', 'FAILED')),
    latency_ms integer NOT NULL,
    question text,
    choice text,
    credits_charged integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX inspiration_session_log_created ON inspiration_session_log (created_at);
CREATE INDEX inspiration_session_log_user ON inspiration_session_log (user_id);

-- Mandatory interview rules (PRD "Mandatory rules per interest"). Seeded here; no Karung editor in v1.
CREATE TABLE inspiration_rules (
    id serial PRIMARY KEY,
    trigger text NOT NULL CONSTRAINT inspiration_rules_trigger_check CHECK (trigger IN ('SKILL_ACTIVITY', 'SHOPPING', 'ABROAD')),
    requirement text NOT NULL CONSTRAINT inspiration_rules_requirement_check
        CHECK (requirement IN ('LEVEL', 'BUDDY', 'BUDGET', 'VISA_NOTE')),
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT inspiration_rules_unique UNIQUE (trigger, requirement)
);
INSERT INTO inspiration_rules (trigger, requirement) VALUES
    ('SKILL_ACTIVITY', 'LEVEL'),
    ('SKILL_ACTIVITY', 'BUDDY'),
    ('SHOPPING', 'BUDGET'),
    ('ABROAD', 'VISA_NOTE');

-- "Ide tersimpan": per account, never in a trip. removed_at/removed_by keep the idea the 31st save pushed out, so the undo
-- can bring it back; such rows are hidden and purged after a day.
CREATE TABLE saved_ideas (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id uuid NOT NULL,
    card_id text NOT NULL,
    card jsonb NOT NULL,
    profile jsonb NOT NULL,
    language text NOT NULL CONSTRAINT saved_ideas_language_check CHECK (language IN ('id', 'en')),
    saved_at timestamptz NOT NULL DEFAULT now(),
    refreshed_at timestamptz NOT NULL DEFAULT now(),
    refresh_requested_at timestamptz,
    removed_at timestamptz,
    removed_by uuid
);
CREATE INDEX saved_ideas_user ON saved_ideas (user_id, saved_at);
CREATE UNIQUE INDEX saved_ideas_user_card ON saved_ideas (user_id, session_id, card_id) WHERE removed_at IS NULL;

-- "Kabari saya" on "Cari buddy": demand for a buddy feature, with the consent time.
CREATE TABLE buddy_interest (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id uuid,
    interest text CONSTRAINT buddy_interest_interest_check CHECK (interest IS NULL OR interest IN
        ('NATURE', 'FOOD', 'CULTURE', 'SHOPPING', 'ADVENTURE', 'NIGHTLIFE', 'PHOTO', 'RELAX')),
    consent_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX buddy_interest_user ON buddy_interest (user_id);
