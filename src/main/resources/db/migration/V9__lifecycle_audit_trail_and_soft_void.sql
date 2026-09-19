-- V9: Lifecycle Audit Trail, Soft Void, Table Transfer Logs, and Bill Check Logs

-- 1. Extend order_items with Soft Void and Order Attribution fields
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS is_voided BOOLEAN DEFAULT FALSE;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS voided_by VARCHAR(64);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS void_approved_by VARCHAR(64);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS void_reason VARCHAR(255);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS is_waste BOOLEAN DEFAULT FALSE;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS ordered_by VARCHAR(64);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS ordered_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_order_items_is_voided ON order_items(is_voided);
CREATE INDEX IF NOT EXISTS idx_order_items_status ON order_items(status);

-- 2. Create table_transfer_logs table
CREATE TABLE IF NOT EXISTS table_transfer_logs (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    from_table_id VARCHAR(64) NOT NULL,
    to_table_id VARCHAR(64) NOT NULL,
    transferred_by VARCHAR(64),
    transferred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_table_transfer_logs_order ON table_transfer_logs(order_id);
CREATE INDEX IF NOT EXISTS idx_table_transfer_logs_transferred_at ON table_transfer_logs(transferred_at);

-- 3. Create bill_check_logs table
CREATE TABLE IF NOT EXISTS bill_check_logs (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    check_sequence INTEGER NOT NULL DEFAULT 1,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_by VARCHAR(64),
    snapshot_total_satang BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bill_check_logs_order ON bill_check_logs(order_id);
CREATE INDEX IF NOT EXISTS idx_bill_check_logs_checked_at ON bill_check_logs(checked_at);
