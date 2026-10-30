-- Receipt lines kept without a "Per item" split: true = the items decide the splits (as before),
-- false = the items are only what the receipt said and the splits were chosen by hand.
ALTER TABLE expenses
    ADD COLUMN items_split BOOLEAN NOT NULL DEFAULT TRUE;
