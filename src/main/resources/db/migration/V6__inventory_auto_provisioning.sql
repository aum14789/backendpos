-- Migration: Support for Auto-Provisioned Raw & Semi Stocks on EOD
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS standard_cost NUMERIC(15, 4) DEFAULT 0.0000;
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS item_type VARCHAR(20) DEFAULT 'RAW';

ALTER TABLE inventory_stocks ADD COLUMN IF NOT EXISTS is_auto_provisioned BOOLEAN DEFAULT false;
