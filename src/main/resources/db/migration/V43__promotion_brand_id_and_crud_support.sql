-- V43: Add brand_id to promotions for multi-brand support (ADR 0006)
ALTER TABLE promotions ADD COLUMN IF NOT EXISTS brand_id VARCHAR(36);
