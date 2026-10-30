-- Karung stage C: settings an admin changes without a deploy. A row is an override of the env var of the same
-- setting; no row = the env var (or the built-in default). Values are text in the shape domain/AdminSettings.kt checks.
CREATE TABLE app_settings (
    key         TEXT        PRIMARY KEY CHECK (key IN (
        'SIGNUP_GRANT', 'DAILY_FREE', 'COST_SEARCH', 'COST_NEXT_BATCH', 'COST_PLACE_DETAIL', 'COST_RECEIPT',
        'AI_MONTHLY_BUDGET_USD', 'AI_BUDGET_ALERT_PERCENT', 'AI_BUDGET_MODE', 'SUGGESTIONS_PER_HOUR', 'LLM_MODEL'
    )),
    value       TEXT        NOT NULL,
    updated_by  UUID        NOT NULL REFERENCES users (id),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- every settings change is audited
ALTER TABLE admin_audit_log DROP CONSTRAINT admin_audit_log_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT admin_audit_log_action CHECK (action IN (
    'CREDITS_ADJUST', 'USER_SIGN_OUT_ALL', 'USER_DISABLE', 'USER_ENABLE', 'PASSWORD_RESET_SEND', 'USERS_EXPORT', 'USER_CREATE',
    'SETTINGS_UPDATE'
));
