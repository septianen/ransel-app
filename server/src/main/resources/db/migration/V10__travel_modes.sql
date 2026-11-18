-- Per-leg travel mode between two itinerary items (Getting there sheet). One row per pair, including a
-- soft-deleted one, so saving Auto (or saving again after a delete) reuses the same id.
CREATE TABLE public.travel_modes (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    from_item_id uuid NOT NULL,
    to_item_id uuid NOT NULL,
    mode text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT travel_modes_pkey PRIMARY KEY (id),
    CONSTRAINT travel_modes_mode_check CHECK ((mode = ANY (ARRAY['WALK'::text, 'MOTORCYCLE'::text, 'CAR'::text, 'PUBLIC_TRANSPORT'::text]))),
    CONSTRAINT travel_modes_pair_diff CHECK ((from_item_id <> to_item_id))
);

CREATE UNIQUE INDEX travel_modes_pair ON public.travel_modes USING btree (from_item_id, to_item_id);
CREATE INDEX idx_travel_modes_trip_updated ON public.travel_modes USING btree (trip_id, updated_at);

CREATE TRIGGER trg_travel_modes_updated_at BEFORE UPDATE ON public.travel_modes FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
