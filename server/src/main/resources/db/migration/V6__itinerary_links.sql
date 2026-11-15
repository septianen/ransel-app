-- Links of an activity (booking, Instagram ...): up to 5 normalized web addresses as a JSON array of strings.
-- booking_url stays as the first link for apps from before the list (the server writes it with every save).
ALTER TABLE itinerary_items ADD COLUMN links jsonb NOT NULL DEFAULT '[]'::jsonb;

-- The existing booking links become the first link. Only real web addresses: free text typed into the old field
-- stays in booking_url, which the old apps still show.
UPDATE itinerary_items
SET links = jsonb_build_array(btrim(booking_url))
WHERE booking_url IS NOT NULL AND btrim(booking_url) ~* '^https?://[^[:space:]]+$' AND length(btrim(booking_url)) <= 500;
