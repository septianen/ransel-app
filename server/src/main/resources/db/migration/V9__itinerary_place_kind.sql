-- What the place of an activity is and the city it is in (plan "Perjalanan antar tempat", 16 Nov 2026). The kind decides
-- whether the activity is a point (a station, a restaurant) or an area (a city) for the travel capsule; the city names the
-- chip of a day with places in several cities. Both nullable: null = not known (typed text, or saved before this column).
-- place_kind holds the constant names of shared PlaceKind.
ALTER TABLE itinerary_items
    ADD COLUMN place_kind text,
    ADD COLUMN place_city text;

ALTER TABLE itinerary_items
    ADD CONSTRAINT itinerary_items_place_kind_check CHECK (place_kind IS NULL OR place_kind IN
        ('CITY', 'REGENCY', 'PROVINCE', 'COUNTRY', 'ATTRACTION', 'AREA')),
    ADD CONSTRAINT itinerary_items_place_city_check CHECK (place_city IS NULL OR (length(btrim(place_city)) >= 1 AND length(place_city) <= 120));
