-- SunPOS V26__crm_demo_seeds.sql
-- Seed complete CRM test dataset: Tiers (STANDARD, SILVER, GOLD), 3 Customers (including near-upgrade), 2 Coupons (FIXED & PERCENT), and Ledger logs

-- 1. Seed / Update Membership Tiers
INSERT INTO membership_tiers (id, company_id, code, name, rank_level, minimum_spent, point_multiplier, discount_percentage, is_active) VALUES
('tier-standard', 'comp-001', 'STANDARD', 'Standard Member (สมาชิกทั่วไป)', 1, 0.0000, 1.00, 0.00, TRUE),
('tier-silver', 'comp-001', 'SILVER', 'Silver Member (สมาชิกซิลเวอร์)', 2, 2000.0000, 1.25, 5.00, TRUE),
('tier-gold', 'comp-001', 'GOLD', 'Gold Member (สมาชิกโกลด์ VIP)', 3, 10000.0000, 1.50, 10.00, TRUE)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    rank_level = EXCLUDED.rank_level,
    minimum_spent = EXCLUDED.minimum_spent,
    point_multiplier = EXCLUDED.point_multiplier,
    discount_percentage = EXCLUDED.discount_percentage,
    is_active = EXCLUDED.is_active;

-- 2. Seed 3 Thai Customers

-- Customer 1: Kittisak Charoensuk (STANDARD Tier, Spent ฿1,800 - Needs ฿200 to upgrade to SILVER!)
INSERT INTO customers (id, company_id, first_name, last_name, display_name, customer_group, primary_branch_id, status) VALUES
('cust-demo-01', 'comp-001', 'กิตติศักดิ์', 'เจริญสุข', 'คุณกิตติศักดิ์ เจริญสุข', 'GENERAL', 'branch-001', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    display_name = EXCLUDED.display_name,
    customer_group = EXCLUDED.customer_group;

INSERT INTO customer_identities (id, company_id, customer_id, identity_type, identity_value, is_primary) VALUES
('ident-demo-01-phone', 'comp-001', 'cust-demo-01', 'PHONE', '0812345678', TRUE),
('ident-demo-01-mem', 'comp-001', 'cust-demo-01', 'MEMBER_ID', 'MEM-1001', FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO customer_memberships (id, customer_id, membership_tier_id, current_spent) VALUES
('cmem-demo-01', 'cust-demo-01', 'tier-standard', 1800.0000)
ON CONFLICT (id) DO UPDATE SET
    membership_tier_id = EXCLUDED.membership_tier_id,
    current_spent = EXCLUDED.current_spent;

INSERT INTO point_ledgers (id, customer_id, transaction_type, points, balance_after, reference_type, reference_id, notes, created_by) VALUES
('pt-demo-01-init', 'cust-demo-01', 'EARN', 72.0000, 72.0000, 'ORDER', 'ORD-INIT-101', 'สะสมแต้มจากการสั่งซื้อสะสม 1,800 บาท', 'system')
ON CONFLICT (id) DO NOTHING;


-- Customer 2: Pimpisa Wongsawat (SILVER Tier, Spent ฿4,500, Points 350 pts)
INSERT INTO customers (id, company_id, first_name, last_name, display_name, customer_group, primary_branch_id, status) VALUES
('cust-demo-02', 'comp-001', 'พิมพิศา', 'วงศ์สวัสดิ์', 'คุณพิมพิศา วงศ์สวัสดิ์', 'VIP', 'branch-001', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    display_name = EXCLUDED.display_name,
    customer_group = EXCLUDED.customer_group;

INSERT INTO customer_identities (id, company_id, customer_id, identity_type, identity_value, is_primary) VALUES
('ident-demo-02-phone', 'comp-001', 'cust-demo-02', 'PHONE', '0898765432', TRUE),
('ident-demo-02-mem', 'comp-001', 'cust-demo-02', 'MEMBER_ID', 'MEM-2002', FALSE),
('ident-demo-02-line', 'comp-001', 'cust-demo-02', 'LINE', 'pimpisa_line', FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO customer_memberships (id, customer_id, membership_tier_id, current_spent) VALUES
('cmem-demo-02', 'cust-demo-02', 'tier-silver', 4500.0000)
ON CONFLICT (id) DO UPDATE SET
    membership_tier_id = EXCLUDED.membership_tier_id,
    current_spent = EXCLUDED.current_spent;

INSERT INTO point_ledgers (id, customer_id, transaction_type, points, balance_after, reference_type, reference_id, notes, created_by) VALUES
('pt-demo-02-init', 'cust-demo-02', 'EARN', 350.0000, 350.0000, 'ORDER', 'ORD-INIT-202', 'สะสมแต้มระดับ Silver (Multiplier 1.25x)', 'system')
ON CONFLICT (id) DO NOTHING;


-- Customer 3: Thanakorn Lertrattanachai (GOLD VIP Tier, Spent ฿15,000, Points 1,200 pts)
INSERT INTO customers (id, company_id, first_name, last_name, display_name, customer_group, primary_branch_id, status) VALUES
('cust-demo-03', 'comp-001', 'ธนากร', 'เลิศรัตนชัย', 'คุณธนากร เลิศรัตนชัย', 'VIP', 'branch-001', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    display_name = EXCLUDED.display_name,
    customer_group = EXCLUDED.customer_group;

INSERT INTO customer_identities (id, company_id, customer_id, identity_type, identity_value, is_primary) VALUES
('ident-demo-03-phone', 'comp-001', 'cust-demo-03', 'PHONE', '0865559988', TRUE),
('ident-demo-03-mem', 'comp-001', 'cust-demo-03', 'MEMBER_ID', 'MEM-3003', FALSE),
('ident-demo-03-line', 'comp-001', 'cust-demo-03', 'LINE', 'thanakorn_vip', FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO customer_memberships (id, customer_id, membership_tier_id, current_spent) VALUES
('cmem-demo-03', 'cust-demo-03', 'tier-gold', 15000.0000)
ON CONFLICT (id) DO UPDATE SET
    membership_tier_id = EXCLUDED.membership_tier_id,
    current_spent = EXCLUDED.current_spent;

INSERT INTO point_ledgers (id, customer_id, transaction_type, points, balance_after, reference_type, reference_id, notes, created_by) VALUES
('pt-demo-03-init', 'cust-demo-03', 'EARN', 1200.0000, 1200.0000, 'ORDER', 'ORD-INIT-303', 'สะสมแต้มระดับ Gold VIP (Multiplier 1.50x)', 'system')
ON CONFLICT (id) DO NOTHING;


-- 3. Seed 2 Standalone MVP Test Coupons

-- Coupon 1: Fixed Discount ฿50 (Min Spend ฿300)
INSERT INTO coupons (
    id, company_id, code, name, description, type, value, min_spend, max_discount,
    usage_limit_total, usage_limit_per_customer, current_uses, is_used, status,
    valid_from, valid_to
) VALUES (
    'coup-demo-fixed-50', 'comp-001', 'SUNFIX50', 'คูปองส่วนลด ฿50 (ขั้นต่ำ ฿300)',
    'รับส่วนลดทันที 50 บาท เมื่อมียอดสั่งซื้อขั้นต่ำ 300 บาทขึ้นไป',
    'FIXED', 50.0000, 300.0000, NULL,
    100, 1, 0, FALSE, 'ACTIVE',
    CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '90 days'
)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    value = EXCLUDED.value,
    min_spend = EXCLUDED.min_spend,
    status = EXCLUDED.status;

-- Coupon 2: Percentage Discount 15% (Min Spend ฿400, Max Discount ฿100)
INSERT INTO coupons (
    id, company_id, code, name, description, type, value, min_spend, max_discount,
    usage_limit_total, usage_limit_per_customer, current_uses, is_used, status,
    valid_from, valid_to
) VALUES (
    'coup-demo-perc-15', 'comp-001', 'SUNPERC15', 'คูปองส่วนลด 15% (ขั้นต่ำ ฿400, สูงสุด ฿100)',
    'รับส่วนลด 15% เมื่อมียอดสั่งซื้อขั้นต่ำ 400 บาท (จำกัดส่วนลดสูงสุด 100 บาท)',
    'PERCENT', 15.0000, 400.0000, 100.0000,
    200, 1, 0, FALSE, 'ACTIVE',
    CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '90 days'
)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    value = EXCLUDED.value,
    min_spend = EXCLUDED.min_spend,
    max_discount = EXCLUDED.max_discount,
    status = EXCLUDED.status;
