-- V25: a member's copy of an owner's list line is theirs to edit or delete (owner, 27 Oct 2026). `customized` marks a
-- copy the member edited (name, quantity or category), so later saves of the list leave it alone.
ALTER TABLE personal_items ADD COLUMN customized BOOLEAN NOT NULL DEFAULT FALSE;
