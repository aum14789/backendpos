-- =============================================================
-- V22: Customer & Identity Schema Enhancements
-- =============================================================
-- Adds:
--   - company_id, display_name, status, updated_at, version to customers
--   - company_id to customer_identities
--   - Index optimizations for normalized phone and multi-identity search
-- =============================================================

-- 1. Alter customers table
ALTER TABLE customers ADD COLUMN IF NOT EXISTS company_id VARCHAR(36) REFERENCES companies(id);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS display_name VARCHAR(200);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Populate existing rows
UPDATE customers
SET company_id = 'comp-001'
WHERE company_id IS NULL;

UPDATE customers
SET display_name = TRIM(first_name || ' ' || COALESCE(last_name, ''))
WHERE display_name IS NULL OR display_name = '';

UPDATE customers
SET status = CASE WHEN is_active THEN 'ACTIVE' ELSE 'INACTIVE' END
WHERE status IS NULL;

UPDATE customers
SET updated_at = now()
WHERE updated_at IS NULL;

-- 2. Alter customer_identities table
ALTER TABLE customer_identities ADD COLUMN IF NOT EXISTS company_id VARCHAR(36) REFERENCES companies(id);

UPDATE customer_identities
SET company_id = 'comp-001'
WHERE company_id IS NULL;

-- 3. Indexes for fast normalized search & company isolation
CREATE INDEX IF NOT EXISTS idx_customers_company_status ON customers(company_id, status);
CREATE INDEX IF NOT EXISTS idx_customers_display_name ON customers(display_name);
CREATE INDEX IF NOT EXISTS idx_customer_identities_company ON customer_identities(company_id, identity_type, identity_value);
