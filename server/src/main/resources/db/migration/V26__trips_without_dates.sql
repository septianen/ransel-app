-- V26: a trip may have no dates yet ("Tanggal belum pasti", PRD "Date poll", owner 28 Oct 2026). The group creates
-- the trip first and picks the dates with a DATE poll. Both dates are set or neither is.
ALTER TABLE trips ALTER COLUMN start_date DROP NOT NULL;
ALTER TABLE trips ALTER COLUMN end_date DROP NOT NULL;
ALTER TABLE trips DROP CONSTRAINT trips_date_range;
ALTER TABLE trips ADD CONSTRAINT trips_date_range CHECK (
    (start_date IS NULL AND end_date IS NULL)
    OR (start_date IS NOT NULL AND end_date IS NOT NULL AND end_date >= start_date)
);

-- A template saved from a trip without dates has no days (day_count 0); all its lines are unscheduled.
ALTER TABLE trip_templates DROP CONSTRAINT trip_templates_day_count_check;
ALTER TABLE trip_templates ADD CONSTRAINT trip_templates_day_count_check CHECK (day_count >= 0);
