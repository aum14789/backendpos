-- V34: Add missing operational and activation columns to branches table & create activation_codes table
ALTER TABLE branches ADD COLUMN IF NOT EXISTS open_time VARCHAR(10) DEFAULT '10:00';
ALTER TABLE branches ADD COLUMN IF NOT EXISTS close_time VARCHAR(10) DEFAULT '22:00';
ALTER TABLE branches ADD COLUMN IF NOT EXISTS ip_address VARCHAR(50);
ALTER TABLE branches ADD COLUMN IF NOT EXISTS dyn_dns_host VARCHAR(255);
ALTER TABLE branches ADD COLUMN IF NOT EXISTS allowed_ip_subnets TEXT;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS activation_code VARCHAR(100);

CREATE TABLE IF NOT EXISTS activation_codes (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    branch_id VARCHAR(36) NOT NULL,
    branch_name VARCHAR(255),
    branch_code VARCHAR(50),
    device_code VARCHAR(50) DEFAULT 'POS-01',
    device_name VARCHAR(255),
    company_id VARCHAR(36),
    company_name VARCHAR(255),
    status VARCHAR(50) DEFAULT 'UNUSED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    activated_at TIMESTAMP WITH TIME ZONE,
    activated_device_id VARCHAR(36),
    created_by VARCHAR(36)
);

CREATE INDEX IF NOT EXISTS idx_activation_codes_code ON activation_codes(code);
CREATE INDEX IF NOT EXISTS idx_activation_codes_branch ON activation_codes(branch_id);
