-- A document can be the receipt photo of an expense (card "foto struk"). One live photo per expense.
ALTER TABLE trip_documents ADD COLUMN expense_id uuid REFERENCES expenses(id);

CREATE UNIQUE INDEX uq_trip_documents_expense ON trip_documents (expense_id) WHERE expense_id IS NOT NULL AND deleted_at IS NULL;
