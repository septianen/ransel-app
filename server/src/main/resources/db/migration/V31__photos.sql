-- B19.1: profile photos. The value is the public path of the image (ApiRoutes.imageFile), like trips.cover_url,
-- which the server now sets itself (PUT /trips/{id}/cover). The JPEG files live in IMAGES_DIR.
ALTER TABLE users ADD COLUMN avatar_url TEXT;
