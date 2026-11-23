-- Android force update (11 Nov 2026): minimum + latest VERSION_NAME and a message replace the force toggle.
-- The old minimum with force off was a dismissable dialog. Dropping the toggle must not turn that into a hard gate.
-- force = true keeps the minimum. force = false, or no force row (the env default was false), moves that minimum
-- to the latest and clears the minimum. iOS is not checked. The check is widened first so the new key can be written,
-- then the retired keys are dropped and the check is tightened so Exposed can read every remaining row.
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
    'ANDROID_FORCE_UPDATE'::text,
    'ANDROID_STORE_URL'::text,
    'ANDROID_LATEST_VERSION'::text,
    'ANDROID_UPDATE_MESSAGE'::text,
    'IOS_MIN_VERSION'::text,
    'IOS_FORCE_UPDATE'::text,
    'IOS_STORE_URL'::text
]));

INSERT INTO app_settings (key, value, updated_by, updated_at)
SELECT 'ANDROID_LATEST_VERSION', min.value, min.updated_by, now()
FROM app_settings min
WHERE min.key = 'ANDROID_MIN_VERSION'
  AND btrim(min.value) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM app_settings force
      WHERE force.key = 'ANDROID_FORCE_UPDATE' AND lower(btrim(force.value)) = 'true'
  );

UPDATE app_settings AS min
SET value = '', updated_at = now()
WHERE min.key = 'ANDROID_MIN_VERSION'
  AND NOT EXISTS (
      SELECT 1 FROM app_settings force
      WHERE force.key = 'ANDROID_FORCE_UPDATE' AND lower(btrim(force.value)) = 'true'
  );

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
