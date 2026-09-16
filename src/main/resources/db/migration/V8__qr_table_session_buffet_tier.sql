-- V8: Add order_type, buffet_tier_id, and buffet_tier_name to qr_table_sessions
ALTER TABLE qr_table_sessions ADD COLUMN IF NOT EXISTS order_type VARCHAR(20) DEFAULT 'DINE_IN';
ALTER TABLE qr_table_sessions ADD COLUMN IF NOT EXISTS buffet_tier_id VARCHAR(64);
ALTER TABLE qr_table_sessions ADD COLUMN IF NOT EXISTS buffet_tier_name VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_qr_table_sessions_buffet_tier ON qr_table_sessions(buffet_tier_id);
