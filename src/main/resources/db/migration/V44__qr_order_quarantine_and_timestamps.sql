-- ADR 0007: QR Order Offline Quarantine and Timestamp Tracking
-- Add lifecycle timestamps to qr_orders
ALTER TABLE qr_orders
ADD COLUMN IF NOT EXISTS cloud_received_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS ordered_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS dispatched_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS printed_at TIMESTAMPTZ;

-- Table for quarantined QR orders that were undelivered due to offline POS / network failure
CREATE TABLE IF NOT EXISTS quarantined_qr_orders (
    id VARCHAR(64) PRIMARY KEY,
    branch_id VARCHAR(64) NOT NULL,
    table_number VARCHAR(32) NOT NULL,
    table_id VARCHAR(64),
    session_id VARCHAR(64),
    total_amount NUMERIC(12, 2) DEFAULT 0.00,
    customer_note TEXT,
    source VARCHAR(32) DEFAULT 'qr',
    idempotency_key VARCHAR(128),
    ordered_at TIMESTAMPTZ,
    cloud_received_at TIMESTAMPTZ,
    quarantined_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(64) DEFAULT 'UNDELIVERED_TIMEOUT',
    raw_payload TEXT,
    is_acknowledged BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_quarantined_qr_orders_branch ON quarantined_qr_orders(branch_id);
CREATE INDEX IF NOT EXISTS idx_quarantined_qr_orders_created ON quarantined_qr_orders(quarantined_at);
