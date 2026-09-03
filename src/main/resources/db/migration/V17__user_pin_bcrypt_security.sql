-- =============================================================
-- V17: User PIN Security & BCrypt Storage Schema Migration
-- =============================================================
-- Enforces VARCHAR(255) for pin_code column to support standard BCrypt hashes (60 chars).
-- Adds index on (company_id, is_active) to optimize PIN authentication scoping.
-- =============================================================

ALTER TABLE users ALTER COLUMN pin_code TYPE VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_users_company_active ON users(company_id, is_active);
