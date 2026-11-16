-- The answers of the "Buat trip" interview and the trip's main way of moving (plans "Wawancara Buat trip" and
-- "Perjalanan antar tempat", 16 Nov 2026). All nullable: null = not answered. origin_* is a city centre the user picked,
-- never a GPS fix. The enum columns hold the constant names of shared DepartureMode, LocalTransport, MainTravelMode.
ALTER TABLE trips
    ADD COLUMN origin_name text,
    ADD COLUMN origin_lat double precision,
    ADD COLUMN origin_lng double precision,
    ADD COLUMN origin_place_id text,
    ADD COLUMN origin_country_code character(2),
    ADD COLUMN departure_mode text,
    ADD COLUMN local_transport text,
    ADD COLUMN main_travel_mode text;

ALTER TABLE trips
    ADD CONSTRAINT trips_origin_name_check CHECK (origin_name IS NULL OR (length(btrim(origin_name)) >= 1 AND length(origin_name) <= 120)),
    ADD CONSTRAINT trips_origin_lat_check CHECK (origin_lat IS NULL OR (origin_lat >= -90 AND origin_lat <= 90)),
    ADD CONSTRAINT trips_origin_lng_check CHECK (origin_lng IS NULL OR (origin_lng >= -180 AND origin_lng <= 180)),
    -- the point is both or neither (like itinerary_latlng_pair), and only an origin with a name has one
    ADD CONSTRAINT trips_origin_latlng_pair CHECK ((origin_lat IS NULL) = (origin_lng IS NULL)),
    ADD CONSTRAINT trips_origin_point_needs_name CHECK (origin_name IS NOT NULL OR (origin_lat IS NULL AND origin_place_id IS NULL AND origin_country_code IS NULL)),
    ADD CONSTRAINT trips_origin_place_id_check CHECK (origin_place_id IS NULL OR (length(btrim(origin_place_id)) >= 1 AND length(origin_place_id) <= 300)),
    ADD CONSTRAINT trips_departure_mode_check CHECK (departure_mode IS NULL OR departure_mode IN
        ('OWN_MOTORCYCLE', 'OWN_CAR', 'TRAVEL_SHUTTLE', 'BUS', 'TRAIN', 'PLANE', 'FERRY')),
    ADD CONSTRAINT trips_local_transport_check CHECK (local_transport IS NULL OR local_transport IN
        ('RENT_MOTORCYCLE', 'RENT_CAR', 'RIDE_HAILING', 'PUBLIC_TRANSPORT', 'TAXI', 'UNKNOWN')),
    ADD CONSTRAINT trips_main_travel_mode_check CHECK (main_travel_mode IS NULL OR main_travel_mode IN
        ('MOTORCYCLE', 'CAR', 'PUBLIC_TRANSPORT'));
