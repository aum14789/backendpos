-- SunPOS V29__zone_type_and_table_management_enhancement.sql
-- Add zone_type and is_active to zones table

ALTER TABLE zones ADD COLUMN IF NOT EXISTS zone_type VARCHAR(50) DEFAULT 'DINE_IN'; -- 'DINE_IN', 'BUFFET'
ALTER TABLE zones ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;

-- Update existing zones demo data
UPDATE zones SET zone_type = 'DINE_IN' WHERE zone_type IS NULL;
