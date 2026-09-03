-- =============================================================
-- V27: Device Capabilities & Capability Audit Logs
-- =============================================================
-- Enables capability-based action gating per device per branch.
-- Capabilities control UI actions, NOT data access.
-- PAY and CLOSE_BUSINESS_DAY are exclusive per branch.
-- =============================================================

CREATE TABLE IF NOT EXISTS device_capabilities (
    id            VARCHAR(36) PRIMARY KEY,
    device_id     VARCHAR(36) NOT NULL REFERENCES devices(id),
    branch_id     VARCHAR(36) NOT NULL REFERENCES branches(id),
    capability    VARCHAR(50) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_by   VARCHAR(36),
    assigned_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(device_id, capability)
);

CREATE INDEX IF NOT EXISTS idx_device_cap_branch ON device_capabilities(branch_id, capability);
CREATE INDEX IF NOT EXISTS idx_device_cap_device ON device_capabilities(device_id);
CREATE INDEX IF NOT EXISTS idx_device_cap_branch_active ON device_capabilities(branch_id, capability, is_active);

-- Audit log for every capability change/reassignment
CREATE TABLE IF NOT EXISTS device_capability_audit_logs (
    id                    VARCHAR(36) PRIMARY KEY,
    device_id             VARCHAR(36) NOT NULL REFERENCES devices(id),
    branch_id             VARCHAR(36) NOT NULL REFERENCES branches(id),
    action                VARCHAR(30) NOT NULL, -- ASSIGNED, REVOKED, TRANSFERRED, REPLACED
    previous_capabilities TEXT,
    new_capabilities      TEXT NOT NULL,
    changed_by            VARCHAR(36),
    reason                VARCHAR(500),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_device_cap_audit_dev ON device_capability_audit_logs(device_id);
CREATE INDEX IF NOT EXISTS idx_device_cap_audit_branch ON device_capability_audit_logs(branch_id);

-- Permission for managing device capabilities
INSERT INTO permissions (id, code, description) VALUES
('perm-devcap-01', 'DEVICE_CAPABILITY_MANAGE', 'Assign and revoke device capabilities per branch')
ON CONFLICT (id) DO NOTHING;

-- Bind to Super Admin (role-01) and Branch Manager (role-02)
INSERT INTO role_permissions (role_id, permission_id) VALUES
('role-01', 'perm-devcap-01'),
('role-02', 'perm-devcap-01')
ON CONFLICT DO NOTHING;

-- ── Demo Seed: Device A & Device B for branch-001 ──
-- Device A: dev-001 (POS Terminal Main)
INSERT INTO devices (id, branch_id, device_name, device_code, device_type, app_version, status) VALUES
('dev-001', 'branch-001', 'POS Terminal Shabu Sukhumvit 01 (Station A)', 'POS-SUK-01', 'POS_MAIN', 'v1.10.0-android', 'ACTIVE'),
('dev-003', 'branch-001', 'Waiter Tablet Sukhumvit 02 (Station B)', 'TAB-SUK-02', 'POS_TABLET', 'v1.10.0-android', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET device_name = EXCLUDED.device_name, device_code = EXCLUDED.device_code;

-- Seed Device A Capabilities: PAY, OPEN_SHIFT, CLOSE_SHIFT, PRINT_RECEIPT, TAKE_ORDER
INSERT INTO device_capabilities (id, device_id, branch_id, capability, is_active, assigned_by) VALUES
('cap-seed-01', 'dev-001', 'branch-001', 'PAY', TRUE, 'usr-001'),
('cap-seed-02', 'dev-001', 'branch-001', 'OPEN_SHIFT', TRUE, 'usr-001'),
('cap-seed-03', 'dev-001', 'branch-001', 'CLOSE_SHIFT', TRUE, 'usr-001'),
('cap-seed-04', 'dev-001', 'branch-001', 'PRINT_RECEIPT', TRUE, 'usr-001'),
('cap-seed-05', 'dev-001', 'branch-001', 'TAKE_ORDER', TRUE, 'usr-001')
ON CONFLICT (device_id, capability) DO UPDATE SET is_active = TRUE;

-- Seed Device B Capabilities: OPEN_TABLE, TAKE_ORDER
INSERT INTO device_capabilities (id, device_id, branch_id, capability, is_active, assigned_by) VALUES
('cap-seed-06', 'dev-003', 'branch-001', 'OPEN_TABLE', TRUE, 'usr-001'),
('cap-seed-07', 'dev-003', 'branch-001', 'TAKE_ORDER', TRUE, 'usr-001')
ON CONFLICT (device_id, capability) DO UPDATE SET is_active = TRUE;
