-- =============================================================
-- V21: CRM Loyalty, Coupons & Demo Customers Seed Migration
-- =============================================================
-- Supports:
--   - Customer Indexes for Order History & Multi-Identity Lookup
--   - Seed Standard Membership Tiers (SILVER, GOLD, PLATINUM)
--   - Seed Verified VIP Customers with Tiers, Multi-Identities & Points
--   - Seed Demo Coupons linked to Promotions (WELCOME50, SUNVIP10)
-- =============================================================

-- 1. Index Optimizations
CREATE INDEX IF NOT EXISTS idx_customer_identities_lookup ON customer_identities(identity_type, identity_value);
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);

-- 2. Seed Standard Membership Tiers
INSERT INTO membership_tiers (id, code, name, rank_level, minimum_spent, point_multiplier, discount_percentage, is_active) VALUES
('tier-silver', 'SILVER', 'สมาชิกทั่วไป (Silver)', 1, 0.0000, 1.00, 0.00, TRUE),
('tier-gold', 'GOLD', 'สมาชิก VIP (Gold)', 2, 5000.0000, 1.50, 5.00, TRUE),
('tier-platinum', 'PLATINUM', 'สมาชิก VVIP (Platinum)', 3, 20000.0000, 2.00, 10.00, TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    rank_level = EXCLUDED.rank_level,
    minimum_spent = EXCLUDED.minimum_spent,
    point_multiplier = EXCLUDED.point_multiplier,
    discount_percentage = EXCLUDED.discount_percentage;

-- 3. Seed Demo Customers with Multi-Identities & Initial Points
-- Customer 1: Somchai Prasert (Gold Member, 500 Points, Phone: 081-111-2222)
INSERT INTO customers (id, first_name, last_name, customer_group, primary_branch_id, is_active) VALUES
('cust-001', 'สมชาย', 'ประเสริฐสุข', 'VIP', 'branch-001', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_identities (id, customer_id, identity_type, identity_value, is_primary) VALUES
('ident-001-phone', 'cust-001', 'PHONE', '081-111-2222', TRUE),
('ident-001-mem', 'cust-001', 'MEMBER_ID', 'MEM-10001', FALSE),
('ident-001-email', 'cust-001', 'EMAIL', 'somchai@sunpos.com', FALSE)
ON CONFLICT (identity_type, identity_value) DO NOTHING;

INSERT INTO customer_memberships (id, customer_id, membership_tier_id, current_spent) VALUES
('mem-001', 'cust-001', 'tier-gold', 8500.0000)
ON CONFLICT (id) DO UPDATE SET membership_tier_id = 'tier-gold', current_spent = 8500.0000;

INSERT INTO point_ledgers (id, customer_id, transaction_type, points, balance_after, reference_type, notes, created_at) VALUES
('pl-001', 'cust-001', 'EARN', 500.0000, 500.0000, 'INITIAL_SEED', 'แต้มสะสมเริ่มต้นต้อนรับสมาชิก VIP', now())
ON CONFLICT (id) DO NOTHING;

-- Customer 2: Somsak Dee (Silver Member, 100 Points, Phone: 081-333-4444)
INSERT INTO customers (id, first_name, last_name, customer_group, primary_branch_id, is_active) VALUES
('cust-002', 'สมศักดิ์', 'ดีงาม', 'GENERAL', 'branch-001', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_identities (id, customer_id, identity_type, identity_value, is_primary) VALUES
('ident-002-phone', 'cust-002', 'PHONE', '081-333-4444', TRUE),
('ident-002-mem', 'cust-002', 'MEMBER_ID', 'MEM-10002', FALSE)
ON CONFLICT (identity_type, identity_value) DO NOTHING;

INSERT INTO customer_memberships (id, customer_id, membership_tier_id, current_spent) VALUES
('mem-002', 'cust-002', 'tier-silver', 1200.0000)
ON CONFLICT (id) DO UPDATE SET membership_tier_id = 'tier-silver', current_spent = 1200.0000;

INSERT INTO point_ledgers (id, customer_id, transaction_type, points, balance_after, reference_type, notes, created_at) VALUES
('pl-002', 'cust-002', 'EARN', 100.0000, 100.0000, 'INITIAL_SEED', 'แต้มสะสมจากการสั่งซื้อ', now())
ON CONFLICT (id) DO NOTHING;

-- Customer 3: Ann Sukjai (Platinum VVIP Member, 1,500 Points, Phone: 089-999-8888, LINE: ann_sukjai)
INSERT INTO customers (id, first_name, last_name, customer_group, primary_branch_id, is_active) VALUES
('cust-003', 'แอน', 'สุขใจ', 'VVIP', 'branch-001', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_identities (id, customer_id, identity_type, identity_value, is_primary) VALUES
('ident-003-phone', 'cust-003', 'PHONE', '089-999-8888', TRUE),
('ident-003-line', 'cust-003', 'LINE', 'ann_sukjai', FALSE),
('ident-003-mem', 'cust-003', 'MEMBER_ID', 'MEM-10003', FALSE)
ON CONFLICT (identity_type, identity_value) DO NOTHING;

INSERT INTO customer_memberships (id, customer_id, membership_tier_id, current_spent) VALUES
('mem-003', 'cust-003', 'tier-platinum', 25000.0000)
ON CONFLICT (id) DO UPDATE SET membership_tier_id = 'tier-platinum', current_spent = 25000.0000;

INSERT INTO point_ledgers (id, customer_id, transaction_type, points, balance_after, reference_type, notes, created_at) VALUES
('pl-003', 'cust-003', 'EARN', 1500.0000, 1500.0000, 'INITIAL_SEED', 'แต้มสะสมสมาชิก Platinum VVIP', now())
ON CONFLICT (id) DO NOTHING;

-- 4. Seed Demo Promotions & Coupons
INSERT INTO promotions (id, code, name, promo_type, priority, start_at, end_at, min_amount, discount_amount, discount_rate) VALUES
('promo-cp-001', 'PROMO-W50', 'ส่วนลดต้อนรับ ฿50', 'FIXED_AMOUNT', 10, now(), now() + interval '365 days', 300.0000, 50.0000, 0.00),
('promo-cp-002', 'PROMO-VIP10', 'ส่วนลดสมาชิก VIP 10%', 'PERCENTAGE', 10, now(), now() + interval '365 days', 500.0000, 0.0000, 10.00)
ON CONFLICT (code) DO NOTHING;

INSERT INTO coupons (id, promotion_id, code, is_used, max_uses, current_uses, expires_at) VALUES
('cp-001', 'promo-cp-001', 'WELCOME50', FALSE, 1000, 0, now() + interval '365 days'),
('cp-002', 'promo-cp-002', 'SUNVIP10', FALSE, 500, 0, now() + interval '365 days')
ON CONFLICT (code) DO NOTHING;
