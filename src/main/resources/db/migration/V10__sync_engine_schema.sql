-- SunPOS V10__sync_engine_schema.sql
-- Offline Synchronization Engine and Device Sync Health Tracking

-- 1. Device Sync States
CREATE TABLE IF NOT EXISTS device_sync_states (
    device_id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    device_name VARCHAR(100) NOT NULL,
    app_version VARCHAR(50) NOT NULL,
    ip_address VARCHAR(50),
    sync_status VARCHAR(50) NOT NULL DEFAULT 'SYNCED', -- SYNCED, SYNCING, PENDING_CHANGES, SYNC_ERROR
    pending_outbox_count INT DEFAULT 0,
    last_synced_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed Sample Device Sync States
INSERT INTO device_sync_states (device_id, branch_id, device_name, app_version, sync_status, pending_outbox_count, last_synced_at) VALUES
('pos-device-001', 'branch-001', 'POS Terminal #1 (สาขาใหญ่)', 'v1.10.0', 'SYNCED', 0, CURRENT_TIMESTAMP),
('pos-device-002', 'branch-001', 'POS Terminal #2 (สาขาใหญ่)', 'v1.10.0', 'PENDING_CHANGES', 2, CURRENT_TIMESTAMP - INTERVAL '5 minutes')
ON CONFLICT (device_id) DO NOTHING;
