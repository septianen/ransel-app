-- The Explore Inspirasi card behind an itinerary item made by "Bikin trip dari sini" (plan idea-activity-detail, owner
-- 23 Nov 2026): Activity details shows it on every device and for every member without an AI call. One row per item.
-- card is the InspirationCardDto as JSON (without photo links); photo_place_id is the Google place of the card photo
-- (signed on every read); region_place_id, lat and lng are the region's point (Google Places, not AI), null when not found.
CREATE TABLE public.itinerary_idea_details (
    item_id uuid NOT NULL,
    trip_id uuid NOT NULL,
    card jsonb NOT NULL,
    photo_place_id text,
    region_place_id text,
    lat double precision,
    lng double precision,
    updated_by uuid,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT itinerary_idea_details_pkey PRIMARY KEY (item_id),
    CONSTRAINT itinerary_idea_details_item_fkey FOREIGN KEY (item_id) REFERENCES public.itinerary_items(id) ON DELETE CASCADE,
    CONSTRAINT itinerary_idea_details_trip_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE,
    CONSTRAINT itinerary_idea_details_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id) ON DELETE SET NULL
);
CREATE INDEX itinerary_idea_details_trip_idx ON public.itinerary_idea_details (trip_id);
