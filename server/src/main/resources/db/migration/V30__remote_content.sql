-- Karung stage D: remote content. Only what differs from the apps' bundled copy is stored. Strings and error
-- messages have a DRAFT row next to the PUBLISHED one; "Terbitkan" moves every draft over and bumps the version.
-- Categories and destination interests go live when saved.

CREATE TABLE content_meta (
    id            SMALLINT    PRIMARY KEY CHECK (id = 1),
    version       INTEGER     NOT NULL DEFAULT 0,
    published_at  TIMESTAMPTZ,
    published_by  UUID        REFERENCES users (id) ON DELETE SET NULL
);
INSERT INTO content_meta (id, version) VALUES (1, 0);

-- an app string (key from server/src/main/resources/content/catalog.json); a null value = the bundled text
CREATE TABLE content_strings (
    key         TEXT        NOT NULL,
    status      TEXT        NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    value_id    TEXT,
    value_en    TEXT,
    updated_by  UUID        REFERENCES users (id) ON DELETE SET NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (key, status)
);

-- the message the apps show for an ErrorCode; null fields = the app's own copy
CREATE TABLE content_errors (
    code          TEXT        NOT NULL,
    status        TEXT        NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    title_id      TEXT,
    title_en      TEXT,
    message_id    TEXT,
    message_en    TEXT,
    button_id     TEXT,
    button_en     TEXT,
    illustration  TEXT,
    display       TEXT        CHECK (display IN ('FULL_SCREEN', 'SNACKBAR', 'DIALOG')),
    updated_by    UUID        REFERENCES users (id) ON DELETE SET NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (code, status)
);

-- how a category of the apps looks; no row = the bundled look
CREATE TABLE content_categories (
    kind        TEXT        NOT NULL CHECK (kind IN ('ITINERARY', 'EXPENSE', 'PACKING')),
    constant    TEXT        NOT NULL,
    label_id    TEXT,
    label_en    TEXT,
    icon        TEXT,
    color       TEXT,
    sort_order  INTEGER     NOT NULL,
    hidden      BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_by  UUID        REFERENCES users (id) ON DELETE SET NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (kind, constant)
);

-- "Khas <destination>" chips curated in Karung, per destination (as in explore_suggestions_cache), month of the year
-- and language: [{"label", "kind": AI|PINNED|MANUAL|HIDDEN}]
CREATE TABLE destination_interests (
    destination  TEXT        NOT NULL,
    month        SMALLINT    NOT NULL CHECK (month BETWEEN 1 AND 12),
    language     TEXT        NOT NULL CHECK (language IN ('id', 'en')),
    chips        JSONB       NOT NULL,
    curated_by   UUID        REFERENCES users (id) ON DELETE SET NULL,
    curated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (destination, month, language)
);

ALTER TABLE admin_audit_log DROP CONSTRAINT admin_audit_log_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT admin_audit_log_action CHECK (action IN (
    'CREDITS_ADJUST', 'USER_SIGN_OUT_ALL', 'USER_DISABLE', 'USER_ENABLE', 'PASSWORD_RESET_SEND', 'USERS_EXPORT', 'USER_CREATE',
    'SETTINGS_UPDATE', 'CONTENT_PUBLISH'
));
