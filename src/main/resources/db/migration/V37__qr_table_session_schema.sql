-- Migration: V37__qr_table_session_schema.sql
-- Description: Dynamic QR Table Session & Token schema for table-level self-ordering

CREATE TABLE IF NOT EXISTS qr_table_sessions (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL,
    table_id VARCHAR(36) NOT NULL,
    table_number VARCHAR(50) NOT NULL,
    session_token VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    opened_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP WITH TIME ZONE,
    opened_by VARCHAR(36),
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_qr_table_sessions_branch_table_status
ON qr_table_sessions (branch_id, table_id, status);

CREATE INDEX IF NOT EXISTS idx_qr_table_sessions_branch_number_status
ON qr_table_sessions (branch_id, table_number, status);

CREATE INDEX IF NOT EXISTS idx_qr_table_sessions_token
ON qr_table_sessions (session_token);
