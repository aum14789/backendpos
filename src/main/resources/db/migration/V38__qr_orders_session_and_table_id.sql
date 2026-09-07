-- Bind QR orders to the open table session so customer history is per round, not all-time.

ALTER TABLE qr_orders
    ADD COLUMN IF NOT EXISTS session_id VARCHAR(36);

ALTER TABLE qr_orders
    ADD COLUMN IF NOT EXISTS table_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_qr_orders_session_id
    ON qr_orders (session_id);

CREATE INDEX IF NOT EXISTS idx_qr_orders_table_id
    ON qr_orders (branch_id, table_id);
