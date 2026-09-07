-- =============================================================
-- V23: Membership Tier and Customer Membership Schema Enhancements
-- =============================================================
-- Adds:
--   - company_id, created_at, updated_at to membership_tiers
--   - updated_at to customer_memberships
--   - performance indexes for tier rank evaluations & multi-company isolation
-- =============================================================

-- 1. Alter membership_tiers table
ALTER TABLE membership_tiers ADD COLUMN IF NOT EXISTS company_id VARCHAR(36) REFERENCES companies(id);
ALTER TABLE membership_tiers ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE membership_tiers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

UPDATE membership_tiers
SET company_id = 'comp-001'
WHERE company_id IS NULL;

-- 2. Alter customer_memberships table
ALTER TABLE customer_memberships ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- 3. Indexes for fast tier lookup, company isolation & rank evaluations
CREATE INDEX IF NOT EXISTS idx_membership_tiers_company_rank ON membership_tiers(company_id, rank_level);
CREATE INDEX IF NOT EXISTS idx_customer_memberships_customer ON customer_memberships(customer_id);
