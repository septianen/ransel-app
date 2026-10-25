-- =====================================================================
-- V17: link previews (TRD "Fetching links that users paste")
-- What a pasted link said about itself (title, picture, site, address and rating from schema.org), kept 7 days per
-- normalised address so the same hotel link pasted by five friends is fetched once. The page itself is never stored,
-- and neither is a price.
-- =====================================================================
CREATE TABLE link_previews (
    url_hash      TEXT        PRIMARY KEY,
    url           TEXT        NOT NULL,
    final_url     TEXT        NOT NULL,
    title         TEXT,
    image_url     TEXT,
    site_name     TEXT,
    address       TEXT,
    rating_value  DOUBLE PRECISION,
    rating_count  INTEGER,
    fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_link_previews_fetched ON link_previews (fetched_at);
