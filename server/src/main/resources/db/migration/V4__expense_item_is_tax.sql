-- Tax is a flag on the line, not a match on its name (11 Nov 2026).
ALTER TABLE expense_items ADD COLUMN is_tax boolean NOT NULL DEFAULT false;

-- Per item: the last line with nobody on it and a price was the tax line. A last line at 0 that nobody
-- ticked is an ordinary item and stays is_tax = false.
UPDATE expense_items AS tax
SET is_tax = true
FROM expenses e
WHERE tax.expense_id = e.id
  AND e.items_split
  AND tax.amount_minor > 0
  AND tax.member_ids = '[]'::jsonb
  AND tax.sort_order = (SELECT max(i.sort_order) FROM expense_items i WHERE i.expense_id = tax.expense_id)
  AND EXISTS (
      SELECT 1 FROM expense_items other
      WHERE other.expense_id = tax.expense_id
        AND other.sort_order <> tax.sort_order
        AND other.member_ids <> '[]'::jsonb
  );

-- Equal split stores no members, so only the app's own default labels can be recovered. A line the user
-- renamed is not guessed.
UPDATE expense_items AS tax
SET is_tax = true
FROM expenses e
WHERE tax.expense_id = e.id
  AND NOT e.items_split
  AND NOT tax.is_tax
  AND tax.name IN ('Pajak dan layanan', 'Tax and service');
