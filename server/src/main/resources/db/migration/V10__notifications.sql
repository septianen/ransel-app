-- =====================================================================
-- V10: notifications (inbox + push)
-- devices                 one row per phone token; a token moves to the user who signed in last
-- notifications           the inbox. Type + params, never rendered text (the app renders it in its own language).
--                         UNIQUE (user_id, dedupe_key): the same event (a sync retry) never notifies twice.
--                         push_state is the outbox: PENDING rows are sent by a background loop after the transaction commits.
-- notification_settings   created lazily; missing row = everything on
-- =====================================================================
CREATE TABLE devices (
    id            UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform      TEXT        NOT NULL CHECK (platform IN ('ANDROID', 'IOS')),
    push_token    TEXT        NOT NULL UNIQUE,
    locale        TEXT        NOT NULL DEFAULT 'id',
    app_version   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    disabled_at   TIMESTAMPTZ
);

CREATE INDEX idx_devices_user ON devices (user_id) WHERE disabled_at IS NULL;

CREATE TABLE notifications (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type           TEXT        NOT NULL CHECK (type IN (
                       'MEMBER_JOINED', 'MEMBER_REMOVED', 'ROLE_CHANGED', 'EXPENSE_ADDED', 'SETTLEMENT_MARKED',
                       'VOTE_REQUESTED', 'VOTE_RESULT', 'PACKING_REMINDER', 'ACTIVITY_REMINDER')),
    trip_id        UUID        REFERENCES trips (id) ON DELETE CASCADE,
    params         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    dedupe_key     TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    read_at        TIMESTAMPTZ,
    push_state     TEXT        NOT NULL DEFAULT 'PENDING' CHECK (push_state IN ('PENDING', 'SENT', 'SKIPPED')),
    push_attempts  SMALLINT    NOT NULL DEFAULT 0,
    UNIQUE (user_id, dedupe_key)
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_pending ON notifications (created_at) WHERE push_state = 'PENDING';

CREATE TABLE notification_settings (
    user_id        UUID    PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    trip_activity  BOOLEAN NOT NULL DEFAULT TRUE,
    expenses       BOOLEAN NOT NULL DEFAULT TRUE,
    reminders      BOOLEAN NOT NULL DEFAULT TRUE
);
