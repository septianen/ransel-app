-- =====================================================================
-- V1: Phases 1-2 — trip, itinerary, packing, expense (solo, no sign-in)
-- PostgreSQL 13+ (gen_random_uuid() is built in)
-- =====================================================================

-- ---------------------------------------------------------------------
-- Shared trigger: updated_at always uses the SERVER clock, not the device clock.
-- This matters for sync in Phase 3 (a user's phone clock can be wrong).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- users (minimal version). Email and password columns are added in V2.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name  TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Dummy user for Phase 2 (before sign-in exists). Fixed ID so it is easy to use.
INSERT INTO users (id, display_name)
VALUES ('00000000-0000-0000-0000-000000000001', 'Local User');

-- ---------------------------------------------------------------------
-- trips
-- ---------------------------------------------------------------------
CREATE TABLE trips (
    id             UUID PRIMARY KEY,               -- created on the client (offline-first)
    owner_id       UUID        NOT NULL REFERENCES users (id),
    title          TEXT        NOT NULL CHECK (length(trim(title)) > 0),
    destination    TEXT        NOT NULL,
    start_date     DATE        NOT NULL,
    end_date       DATE        NOT NULL,
    base_currency  CHAR(3)     NOT NULL DEFAULT 'IDR' CHECK (base_currency ~ '^[A-Z]{3}$'),
    budget_minor   BIGINT      CHECK (budget_minor IS NULL OR budget_minor >= 0),
    cover_url      TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT trips_date_range CHECK (end_date >= start_date)
);

CREATE INDEX idx_trips_owner_active ON trips (owner_id, start_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_trips_owner_updated ON trips (owner_id, updated_at);   -- for GET /sync?since=

CREATE TRIGGER trg_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- itinerary_items
-- day_date NULL  = "Belum dijadwalkan" / Unscheduled (e.g. a right swipe from AI)
-- sort_order     = fractional number; inserting between A and B = (A + B) / 2
-- category       = same enum as expenses.category (see itinerary-spec.md)
-- duration_min   = duration estimate from AI, used when scheduling from Unscheduled
-- ---------------------------------------------------------------------
CREATE TABLE itinerary_items (
    id                      UUID PRIMARY KEY,
    trip_id                 UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    day_date                DATE,
    start_time              TIME,
    end_time                TIME,
    title                   TEXT        NOT NULL CHECK (length(trim(title)) > 0),
    category                TEXT        NOT NULL DEFAULT 'OTHER'
                            CHECK (category IN ('ATTRACTION', 'FOOD', 'TRANSPORT', 'LODGING', 'SHOPPING', 'OTHER')),
    place_name              TEXT,
    place_id                TEXT,                  -- Google placeId (may be stored permanently)
    lat                     DOUBLE PRECISION CHECK (lat BETWEEN -90 AND 90),
    lng                     DOUBLE PRECISION CHECK (lng BETWEEN -180 AND 180),
    duration_min            INTEGER     CHECK (duration_min IS NULL OR duration_min > 0),
    notes                   TEXT,
    booking_url             TEXT,
    source                  TEXT        NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'AI')),
    sort_order              DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT itinerary_time_range CHECK (end_time IS NULL OR start_time IS NULL OR end_time >= start_time),
    CONSTRAINT itinerary_latlng_pair CHECK ((lat IS NULL) = (lng IS NULL))
);

CREATE INDEX idx_itinerary_trip_day ON itinerary_items (trip_id, day_date, sort_order) WHERE deleted_at IS NULL;
CREATE INDEX idx_itinerary_trip_updated ON itinerary_items (trip_id, updated_at);

CREATE TRIGGER trg_itinerary_updated_at
    BEFORE UPDATE ON itinerary_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- packing_items
-- ---------------------------------------------------------------------
CREATE TABLE packing_items (
    id          UUID PRIMARY KEY,
    trip_id     UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    category    TEXT        NOT NULL DEFAULT 'OTHER'
                CHECK (category IN ('CLOTHING', 'TOILETRIES', 'ELECTRONICS', 'DOCUMENTS', 'HEALTH', 'OTHER')),
    name        TEXT        NOT NULL CHECK (length(trim(name)) > 0),
    quantity    INTEGER     NOT NULL DEFAULT 1 CHECK (quantity > 0),
    is_packed   BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order  DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_packing_trip ON packing_items (trip_id, category, sort_order) WHERE deleted_at IS NULL;
CREATE INDEX idx_packing_trip_updated ON packing_items (trip_id, updated_at);

CREATE TRIGGER trg_packing_updated_at
    BEFORE UPDATE ON packing_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- packing_templates
-- items = [{"category":"CLOTHING","name":"Kaos","quantity":3}, ...]
-- JSONB because a template is always read and written as a whole.
-- owner_id NULL = built-in template (beach, city trip, mountain hike), seeded by the server.
-- Checked state is not stored; quantity may be omitted ("Simpan jumlah" / Save quantity off).
-- ---------------------------------------------------------------------
CREATE TABLE packing_templates (
    id          UUID PRIMARY KEY,
    owner_id    UUID        REFERENCES users (id),
    name        TEXT        NOT NULL CHECK (length(trim(name)) > 0),
    items       JSONB       NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(items) = 'array'),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_templates_owner ON packing_templates (owner_id, updated_at);

CREATE TRIGGER trg_templates_updated_at
    BEFORE UPDATE ON packing_templates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- expenses
-- amount_minor    : amount in the smallest unit of its currency (Rp50.000 = 50000, USD 12.50 = 1250)
-- fx_rate_to_base : rate at transaction time -> 1 unit of `currency` = x units of the trip base_currency.
--                   Stored so old totals do not change when rates change. NULL when equal to base.
-- category        : same enum as itinerary_items.category; app labels are nouns
--                   (Activities, Food, Transport, Lodging, Shopping, Other).
-- payment_method  : the "Bayar pakai" / Paid with field (Cash, Credit card, Transfer, ...).
-- ---------------------------------------------------------------------
CREATE TABLE expenses (
    id               UUID PRIMARY KEY,
    trip_id          UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    amount_minor     BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency         CHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    fx_rate_to_base  NUMERIC(20, 10) CHECK (fx_rate_to_base IS NULL OR fx_rate_to_base > 0),
    category         TEXT        NOT NULL DEFAULT 'OTHER'
                     CHECK (category IN ('ATTRACTION', 'FOOD', 'TRANSPORT', 'LODGING', 'SHOPPING', 'OTHER')),
    payment_method   TEXT        NOT NULL DEFAULT 'CASH'
                     CHECK (payment_method IN ('CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'TRANSFER', 'E_WALLET', 'OTHER')),
    note             TEXT,
    spent_at         TIMESTAMPTZ NOT NULL,
    paid_by_user_id  UUID        NOT NULL REFERENCES users (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);

CREATE INDEX idx_expenses_trip_spent ON expenses (trip_id, spent_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_expenses_trip_updated ON expenses (trip_id, updated_at);

CREATE TRIGGER trg_expenses_updated_at
    BEFORE UPDATE ON expenses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
