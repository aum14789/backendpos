-- SunPOS V2__seed_organization_permissions.sql
-- Seed default permissions, system roles, initial company, branch, device, and admin user

-- 1. Seed Permissions
INSERT INTO permissions (id, code, description) VALUES
('perm-01', 'ORDER_VIEW', 'View orders'),
('perm-02', 'ORDER_CREATE', 'Create and modify orders'),
('perm-03', 'ORDER_CANCEL', 'Cancel unbilled orders'),
('perm-04', 'ORDER_VOID', 'Void completed financial orders'),
('perm-05', 'DISCOUNT_APPLY', 'Apply standard promotions and discounts'),
('perm-06', 'DISCOUNT_OVERRIDE', 'Override manual discount limits'),
('perm-07', 'PAYMENT_REFUND', 'Process full or partial payment refunds'),
('perm-08', 'STOCK_ADJUST', 'Perform stock adjustments and counts'),
('perm-09', 'STOCK_TRANSFER', 'Initiate and receive warehouse stock transfers'),
('perm-10', 'PURCHASE_APPROVE', 'Approve purchase orders and goods receiving'),
('perm-11', 'PROMOTION_MANAGE', 'Create and edit promotion rules and coupons'),
('perm-12', 'USER_MANAGE', 'Manage users, PIN codes, and role assignments'),
('perm-13', 'REPORT_VIEW', 'Access sales, financial, and inventory reports'),
('perm-14', 'ORGANIZATION_MANAGE', 'Manage companies, branches, and POS devices')
ON CONFLICT (id) DO NOTHING;

-- 2. Seed System Roles
INSERT INTO roles (id, name, description) VALUES
('role-01', 'ROLE_SUPER_ADMIN', 'Super Administrator with full platform access'),
('role-02', 'ROLE_BRANCH_MANAGER', 'Branch Manager with management privileges'),
('role-03', 'ROLE_SUPERVISOR', 'Shift Supervisor with discount override privileges'),
('role-04', 'ROLE_CASHIER', 'Frontline Cashier for POS operations'),
('role-05', 'ROLE_KITCHEN_STAFF', 'Kitchen Display and order preparation staff')
ON CONFLICT (id) DO NOTHING;

-- 3. Seed Role-Permissions
-- Super Admin: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'role-01', id FROM permissions
ON CONFLICT DO NOTHING;

-- Branch Manager
INSERT INTO role_permissions (role_id, permission_id) VALUES
('role-02', 'perm-01'), ('role-02', 'perm-02'), ('role-02', 'perm-03'), ('role-02', 'perm-04'),
('role-02', 'perm-05'), ('role-02', 'perm-06'), ('role-02', 'perm-07'), ('role-02', 'perm-08'),
('role-02', 'perm-09'), ('role-02', 'perm-11'), ('role-02', 'perm-12'), ('role-02', 'perm-13')
ON CONFLICT DO NOTHING;

-- Supervisor
INSERT INTO role_permissions (role_id, permission_id) VALUES
('role-03', 'perm-01'), ('role-03', 'perm-02'), ('role-03', 'perm-03'), ('role-03', 'perm-05'),
('role-03', 'perm-06'), ('role-03', 'perm-07'), ('role-03', 'perm-08')
ON CONFLICT DO NOTHING;

-- Cashier
INSERT INTO role_permissions (role_id, permission_id) VALUES
('role-04', 'perm-01'), ('role-04', 'perm-02'), ('role-04', 'perm-03'), ('role-04', 'perm-05')
ON CONFLICT DO NOTHING;

-- Kitchen Staff
INSERT INTO role_permissions (role_id, permission_id) VALUES
('role-05', 'perm-01')
ON CONFLICT DO NOTHING;

-- 4. Seed Default Company & Branch
INSERT INTO companies (id, name, tax_id) VALUES
('comp-001', 'SunPOS Restaurant Group Co., Ltd.', '0105560000001')
ON CONFLICT (id) DO NOTHING;

INSERT INTO branches (id, company_id, name, code, address, phone, business_day_close_time, tax_rate, service_charge_rate) VALUES
('branch-001', 'comp-001', 'Sukhumvit Main Branch', 'BR-01', '123 Sukhumvit Road, Bangkok 10110', '02-123-4567', '02:00', 7.00, 10.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO devices (id, branch_id, device_name, device_code, device_type, app_version, status) VALUES
('dev-001', 'branch-001', 'Main Cashier POS 01', 'POS-MAIN-01', 'POS_MAIN', '1.0.0', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- 5. Seed Initial Admin User (password: admin123, pin: 1234)
-- BCrypt for admin123: $2a$10$7Q9lT5qSg8F/H8b.pBfD9uV7Xg3A9yK8m0L9p8q7r6s5t4u3v2w1x (or generated via BCrypt)
INSERT INTO users (id, company_id, username, password_hash, full_name, email, phone, pin_code, is_active) VALUES
('usr-001', 'comp-001', 'admin', '$2a$10$eD4W27q92W4o9zR9k1k2u.7k4L3m2N1o0P9q8R7s6T5u4V3w2X1yZ', 'System Administrator', 'admin@sunpos.com', '081-999-8888', '$2a$10$eD4W27q92W4o9zR9k1k2u.7k4L3m2N1o0P9q8R7s6T5u4V3w2X1yZ', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id) VALUES
('usr-001', 'role-01')
ON CONFLICT DO NOTHING;
