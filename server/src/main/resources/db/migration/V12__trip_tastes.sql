-- A trip's Seleramu (plan explore-trip-one-tap, owner 22 Nov 2026): the answers of the "Ubah seleramu" sheet, kept with
-- the trip so "Cari tempat" opens prefilled on every device and for every member. One row per trip; no row = not set.
-- interests holds Interest constant names; extra_interests the "Khas" chips (at most 5, each at most 40 characters).
CREATE TABLE public.trip_tastes (
    trip_id uuid NOT NULL,
    interests text[] DEFAULT '{}'::text[] NOT NULL,
    extra_interests text[] DEFAULT '{}'::text[] NOT NULL,
    style text,
    price_level text,
    notes text,
    updated_by uuid,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT trip_tastes_pkey PRIMARY KEY (trip_id),
    CONSTRAINT trip_tastes_trip_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE,
    CONSTRAINT trip_tastes_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id) ON DELETE SET NULL,
    CONSTRAINT trip_tastes_style_check CHECK ((style IS NULL) OR (style = ANY (ARRAY['SANTAI'::text, 'PADAT'::text]))),
    CONSTRAINT trip_tastes_price_level_check CHECK ((price_level IS NULL) OR (price_level = ANY (ARRAY['LOW'::text, 'MEDIUM'::text, 'HIGH'::text]))),
    CONSTRAINT trip_tastes_extra_count CHECK ((cardinality(extra_interests) <= 5)),
    CONSTRAINT trip_tastes_notes_length CHECK (((notes IS NULL) OR (length(notes) <= 500)))
);
