-- Migration: Branch Classification, Pre-Opening Lifecycle, Cashless and Invoice Sequence
ALTER TABLE branches ADD COLUMN IF NOT EXISTS is_test_branch BOOLEAN DEFAULT false;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'PRE_OPENING';
ALTER TABLE branches ADD COLUMN IF NOT EXISTS allow_cash_payment BOOLEAN DEFAULT true;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS invoice_sequence_number BIGINT DEFAULT 0;

ALTER TABLE brands ADD COLUMN IF NOT EXISTS allow_cash_payment BOOLEAN DEFAULT true;
