-- Karung: the app update dialog (minimum version, force flag and store link per platform) lives in app_settings, so
-- its keys join the allowed list. The constraint name is the one Postgres gave the inline CHECK in V29.
ALTER TABLE app_settings DROP CONSTRAINT app_settings_key_check;
ALTER TABLE app_settings ADD CONSTRAINT app_settings_key_check CHECK (key IN (
    'SIGNUP_GRANT', 'DAILY_FREE', 'COST_SEARCH', 'COST_NEXT_BATCH', 'COST_PLACE_DETAIL', 'COST_RECEIPT',
    'AI_MONTHLY_BUDGET_USD', 'AI_BUDGET_ALERT_PERCENT', 'AI_BUDGET_MODE', 'SUGGESTIONS_PER_HOUR', 'LLM_MODEL',
    'ANDROID_MIN_VERSION', 'ANDROID_FORCE_UPDATE', 'ANDROID_STORE_URL',
    'IOS_MIN_VERSION', 'IOS_FORCE_UPDATE', 'IOS_STORE_URL'
));
