-- V4__retire_stock_deduction_switches.sql
-- ADR 0032 / Spec 0034: Retire stock_deduction_mode and allow_negative_stock
-- End-of-day recipe consumption now permits negative stock unconditionally.
-- The unbacked REALTIME mode is retired and all branches are restored to EOD.

-- Step 1: Migrate any branch configured for REALTIME back to EOD
UPDATE inventory_branch_configs
SET stock_deduction_mode = 'EOD'
WHERE stock_deduction_mode != 'EOD';

-- Step 2: Drop the obsolete columns
ALTER TABLE inventory_branch_configs DROP COLUMN IF EXISTS stock_deduction_mode;
ALTER TABLE inventory_branch_configs DROP COLUMN IF EXISTS allow_negative_stock;
