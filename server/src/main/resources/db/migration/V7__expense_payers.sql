-- =====================================================================
-- V7: several payers per expense (PRD "Shared expenses")
-- An expense paid by more than one person (A paid 200.000, B paid 100.000 of a 300.000 bill).
-- Rule (checked in the service, not the DB): two or more rows whose amount_minor add up to expenses.amount_minor,
-- in that expense's currency. expenses.paid_by_user_id is the first of them. No rows = paid_by_user_id paid all.
-- Synced as part of the expense: when the expense changes, its payers are replaced as a whole.
-- =====================================================================
CREATE TABLE expense_payers (
    expense_id    UUID   NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    user_id       UUID   NOT NULL REFERENCES users (id),
    amount_minor  BIGINT NOT NULL CHECK (amount_minor > 0),
    PRIMARY KEY (expense_id, user_id)
);

CREATE INDEX idx_expense_payers_user ON expense_payers (user_id);
