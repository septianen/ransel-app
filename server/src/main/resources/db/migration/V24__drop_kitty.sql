-- V24: the kitty ("Kas") is gone (owner, 27 Oct 2026; shared/CONTRACT_CHANGES.md). It never reached production, so
-- only staging data goes. An expense that was paid from the kitty keeps its payer (the treasurer of that time); the
-- deposits leave the balances with the tables.
DROP TABLE IF EXISTS kitty_deposits;
DROP TABLE IF EXISTS kitty;
ALTER TABLE expenses DROP COLUMN IF EXISTS paid_from_kitty;
