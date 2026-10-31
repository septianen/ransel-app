-- =====================================================================
-- V32: trip albums (owner, 31 Oct 2026). Links to photos the members keep in their own Google Drive (or Google
-- Photos, iCloud, ...). RANSEL stores only the link: a public Drive folder is listed with the server's Drive API key
-- and the images load straight from Google; every other link opens outside the app.
-- =====================================================================
CREATE TABLE trip_albums (
    id                  UUID        PRIMARY KEY,
    trip_id             UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    url                 TEXT        NOT NULL CHECK (length(url) BETWEEN 1 AND 2000),
    provider            TEXT        NOT NULL CHECK (provider IN ('DRIVE_FOLDER', 'GOOGLE_PHOTOS', 'OTHER')),
    drive_folder_id     TEXT,
    resource_key        TEXT,
    title               TEXT        CHECK (title IS NULL OR length(trim(title)) BETWEEN 1 AND 80),
    added_by_member_id  UUID        NOT NULL REFERENCES trip_members (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CHECK ((provider = 'DRIVE_FOLDER') = (drive_folder_id IS NOT NULL))
);
CREATE INDEX trip_albums_trip ON trip_albums (trip_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_trip_albums_updated_at
    BEFORE UPDATE ON trip_albums
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
