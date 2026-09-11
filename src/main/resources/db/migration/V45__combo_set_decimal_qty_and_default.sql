-- SunPOS V45: Combo / Set Menu (Type 'S') Sequence, Default Choice, and Decimal Quantity
ALTER TABLE combo_choices ADD COLUMN IF NOT EXISTS quantity NUMERIC(10, 4) NOT NULL DEFAULT 1.0;
ALTER TABLE combo_choices ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE combo_groups ADD COLUMN IF NOT EXISTS seq_order INT NOT NULL DEFAULT 1;

ALTER TABLE order_combo_snapshots ADD COLUMN IF NOT EXISTS quantity NUMERIC(10, 4) NOT NULL DEFAULT 1.0;
