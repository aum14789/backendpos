-- SunPOS V9__line_integration_schema.sql
-- LINE Official Account Configuration and Notification Audit Logs

-- 1. LINE OA Configs
CREATE TABLE IF NOT EXISTS line_oa_configs (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) REFERENCES branches(id),
    channel_id VARCHAR(100) NOT NULL UNIQUE,
    channel_secret VARCHAR(255) NOT NULL,
    channel_access_token TEXT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Notification Audit Logs & Retry Queue
CREATE TABLE IF NOT EXISTS notification_logs (
    id VARCHAR(36) PRIMARY KEY,
    recipient_id VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL DEFAULT 'LINE', -- LINE, SMS, EMAIL
    template_type VARCHAR(50) NOT NULL, -- ORDER_CONFIRMED, KITCHEN_PREPARING, ORDER_READY, ORDER_COMPLETED, COUPON_ISSUED
    payload_json TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, DELIVERED, FAILED, RETRYING
    retry_count INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_notification_status ON notification_logs(status);
CREATE INDEX IF NOT EXISTS idx_notification_recipient ON notification_logs(recipient_id);

-- Seed Sample LINE OA Config
INSERT INTO line_oa_configs (id, branch_id, channel_id, channel_secret, channel_access_token) VALUES
('loc-001', 'branch-001', '1657890123', 'secret_key_sample_123', 'access_token_sample_abc123')
ON CONFLICT (id) DO NOTHING;

-- Seed Sample Notification Log
INSERT INTO notification_logs (id, recipient_id, channel, template_type, payload_json, status, sent_at) VALUES
('nl-001', 'U1234567890abcdef', 'LINE', 'ORDER_CONFIRMED', '{"orderId":"ORD-20260827-1001","amount":"120.00"}', 'DELIVERED', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
