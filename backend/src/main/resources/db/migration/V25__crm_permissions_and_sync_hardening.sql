-- SunPOS V25__crm_permissions_and_sync_hardening.sql
-- Seed granular CRM permissions and bind them to cashier, manager, and administrator roles

-- 1. Insert CRM Permissions
INSERT INTO permissions (id, code, description) VALUES
('perm-crm-01', 'crm.customer.read', 'View customer profiles, search customer CRM and loyalty points'),
('perm-crm-02', 'crm.customer.write', 'Register new customers and update customer profile details'),
('perm-crm-03', 'crm.points.redeem', 'Redeem loyalty points for order discount at POS checkout'),
('perm-crm-04', 'crm.points.adjust', 'Manually adjust customer loyalty points with mandatory reason'),
('perm-crm-05', 'crm.coupon.manage', 'Create, update, and manage coupon campaigns and validation limits'),
('perm-crm-06', 'CUSTOMER_VIEW', 'Alias for crm.customer.read'),
('perm-crm-07', 'CUSTOMER_MANAGE', 'Alias for crm.customer.write'),
('perm-crm-08', 'POINT_REDEEM', 'Alias for crm.points.redeem'),
('perm-crm-09', 'POINT_ADJUST', 'Alias for crm.points.adjust'),
('perm-crm-10', 'COUPON_MANAGE', 'Alias for crm.coupon.manage')
ON CONFLICT (id) DO NOTHING;

-- 2. Bind Permissions to Roles

-- Super Admin (role-01): Gets all CRM permissions
INSERT INTO role_permissions (role_id, permission_id) VALUES
('role-01', 'perm-crm-01'),
('role-01', 'perm-crm-02'),
('role-01', 'perm-crm-03'),
('role-01', 'perm-crm-04'),
('role-01', 'perm-crm-05'),
('role-01', 'perm-crm-06'),
('role-01', 'perm-crm-07'),
('role-01', 'perm-crm-08'),
('role-01', 'perm-crm-09'),
('role-01', 'perm-crm-10')
ON CONFLICT DO NOTHING;

-- Branch / Store Manager (role-02): Gets read/write customer, redeem points, adjust points, and coupon manage
INSERT INTO role_permissions (role_id, permission_id) VALUES
('role-02', 'perm-crm-01'),
('role-02', 'perm-crm-02'),
('role-02', 'perm-crm-03'),
('role-02', 'perm-crm-04'),
('role-02', 'perm-crm-05'),
('role-02', 'perm-crm-06'),
('role-02', 'perm-crm-07'),
('role-02', 'perm-crm-08'),
('role-02', 'perm-crm-09'),
('role-02', 'perm-crm-10')
ON CONFLICT DO NOTHING;

-- Supervisor (role-03): Gets read/write customer and redeem points
INSERT INTO role_permissions (role_id, permission_id) VALUES
('role-03', 'perm-crm-01'),
('role-03', 'perm-crm-02'),
('role-03', 'perm-crm-03'),
('role-03', 'perm-crm-06'),
('role-03', 'perm-crm-07'),
('role-03', 'perm-crm-08')
ON CONFLICT DO NOTHING;

-- Frontline Cashier (role-04): Gets read/write customer and redeem points (CANNOT adjust points or manage coupons)
INSERT INTO role_permissions (role_id, permission_id) VALUES
('role-04', 'perm-crm-01'),
('role-04', 'perm-crm-02'),
('role-04', 'perm-crm-03'),
('role-04', 'perm-crm-06'),
('role-04', 'perm-crm-07'),
('role-04', 'perm-crm-08')
ON CONFLICT DO NOTHING;
