-- Android force update (11 Nov 2026): minimum + latest VERSION_NAME and a message replace the force toggle.
-- iOS is not checked. Drop those keys before the check changes so Exposed can read every remaining row.
DELETE FROM app_settings WHERE key IN (
    'ANDROID_FORCE_UPDATE',
    'IOS_MIN_VERSION',
    'IOS_FORCE_UPDATE',
    'IOS_STORE_URL'
);

ALTER TABLE app_settings DROP CONSTRAINT app_settings_key_check;
ALTER TABLE app_settings ADD CONSTRAINT app_settings_key_check CHECK (key = ANY (ARRAY[
    'SIGNUP_GRANT'::text,
    'DAILY_FREE'::text,
    'COST_SEARCH'::text,
    'COST_NEXT_BATCH'::text,
    'COST_PLACE_DETAIL'::text,
    'COST_RECEIPT'::text,
    'AI_MONTHLY_BUDGET_USD'::text,
    'AI_BUDGET_ALERT_PERCENT'::text,
    'AI_BUDGET_MODE'::text,
    'SUGGESTIONS_PER_HOUR'::text,
    'LLM_MODEL'::text,
    'ANDROID_MIN_VERSION'::text,
    'ANDROID_LATEST_VERSION'::text,
    'ANDROID_UPDATE_MESSAGE'::text,
    'ANDROID_STORE_URL'::text
]));
