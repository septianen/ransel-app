-- =====================================================================
-- V20: overseas trips (PRD "Overseas trips")
--   trips        country, local currency and IANA zone of the destination; set from the picked place, editable.
--   expenses     where the rate came from (DAILY reference rate or MANUAL) and the day of the reference rate.
--                The amount stays in the expense's own currency with fx_rate_to_base, as since Phase 2: that is the
--                original amount the TRD of 24 Oct wants kept, so no orig_amount/orig_currency columns.
--   fx_rates     ECB euro reference rates per day, filled once a day; other pairs are crossed through EUR.
-- =====================================================================
ALTER TABLE trips
    ADD COLUMN country_code   CHAR(2),
    ADD COLUMN local_currency CHAR(3),
    ADD COLUMN time_zone      TEXT CHECK (time_zone IS NULL OR length(time_zone) BETWEEN 1 AND 64);

ALTER TABLE expenses
    ADD COLUMN fx_source TEXT CHECK (fx_source IN ('DAILY', 'MANUAL')),
    ADD COLUMN fx_date   DATE;

CREATE TABLE fx_rates (
    rate_date  DATE           NOT NULL,
    currency   CHAR(3)        NOT NULL,
    -- units of `currency` for 1 EUR
    per_euro   NUMERIC(20, 8) NOT NULL CHECK (per_euro > 0),
    PRIMARY KEY (rate_date, currency)
);
