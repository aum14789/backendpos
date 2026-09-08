-- V41: Add missing is_active column to devices table
-- Required because Device entity has isActive field and JdbcRepository reflects all fields into INSERT/UPDATE

ALTER TABLE devices ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Optionally backfill any existing rows (already handled by DEFAULT)
UPDATE devices SET is_active = TRUE WHERE is_active IS NULL;
