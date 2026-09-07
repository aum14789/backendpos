-- SunPOS V8__crm_membership_loyalty_schema.sql
-- Customers, Multiple Identities, Membership Tiers, Customer Memberships, Point Ledger, and Customer Segments

-- 1. Customers
CREATE TABLE IF NOT EXISTS customers (
    id VARCHAR(36) PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),
    gender VARCHAR(20),
    birth_date TIMESTAMP WITH TIME ZONE,
    customer_group VARCHAR(50) DEFAULT 'GENERAL',
    primary_branch_id VARCHAR(36) REFERENCES branches(id),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Customer Identities (Multiple Identities per Customer)
CREATE TABLE IF NOT EXISTS customer_identities (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    identity_type VARCHAR(50) NOT NULL, -- PHONE, LINE, MEMBER_ID, EMAIL, OTHER
    identity_value VARCHAR(255) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_identity_type_value UNIQUE (identity_type, identity_value)
);

-- 3. Membership Tiers
CREATE TABLE IF NOT EXISTS membership_tiers (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    rank_level INT NOT NULL DEFAULT 1,
    minimum_spent NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    point_multiplier NUMERIC(5, 2) NOT NULL DEFAULT 1.00,
    discount_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE
);

-- 4. Customer Memberships
CREATE TABLE IF NOT EXISTS customer_memberships (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    membership_tier_id VARCHAR(36) NOT NULL REFERENCES membership_tiers(id),
    effective_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expiration_date TIMESTAMP WITH TIME ZONE,
    current_spent NUMERIC(15, 4) DEFAULT 0.0000
);

-- 5. Point Ledger (Immutable Point Audit Trail)
CREATE TABLE IF NOT EXISTS point_ledgers (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL REFERENCES customers(id),
    transaction_type VARCHAR(50) NOT NULL, -- EARN, REDEEM, ADJUST, EXPIRE, REVERSE
    points NUMERIC(12, 4) NOT NULL,
    balance_after NUMERIC(12, 4) NOT NULL,
    reference_type VARCHAR(50), -- ORDER, MANUAL_ADJUST, REVERSAL
    reference_id VARCHAR(36),
    notes TEXT,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. Customer Segments
CREATE TABLE IF NOT EXISTS customer_segments (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    min_purchase_frequency INT DEFAULT 0,
    min_total_spending NUMERIC(15, 4) DEFAULT 0.0000,
    max_recency_days INT DEFAULT 365,
    favorite_category VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE
);

-- 7. Add customer_id to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_id VARCHAR(36) REFERENCES customers(id);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_customer_identities ON customer_identities(identity_type, identity_value);
CREATE INDEX IF NOT EXISTS idx_point_ledger_customer ON point_ledgers(customer_id);

-- Seed Membership Tiers
INSERT INTO membership_tiers (id, code, name, rank_level, minimum_spent, point_multiplier, discount_percentage) VALUES
('tier-silver', 'SILVER', 'สมาชิกทั่วไป (Silver)', 1, 0.0000, 1.00, 0.00),
('tier-gold', 'GOLD', 'สมาชิก VIP (Gold)', 2, 5000.0000, 1.50, 5.00),
('tier-platinum', 'PLATINUM', 'สมาชิก VVIP (Platinum)', 3, 20000.0000, 2.00, 10.00)
ON CONFLICT (id) DO NOTHING;

-- Seed Sample Customer
INSERT INTO customers (id, first_name, last_name, customer_group, primary_branch_id) VALUES
('cust-001', 'สมชาย', 'ใจดี', 'VIP', 'branch-001')
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_identities (id, customer_id, identity_type, identity_value, is_primary) VALUES
('ident-001', 'cust-001', 'PHONE', '0812345678', TRUE),
('ident-002', 'cust-001', 'MEMBER_ID', 'MEM-10001', FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_memberships (id, customer_id, membership_tier_id, current_spent) VALUES
('cmem-001', 'cust-001', 'tier-gold', 6500.0000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO point_ledgers (id, customer_id, transaction_type, points, balance_after, reference_type, reference_id, notes) VALUES
('pt-001', 'cust-001', 'EARN', 120.0000, 120.0000, 'ORDER', 'ORD-20260827-1001', 'สะสมแต้มจากการสั่งซื้อออเดอร์')
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_segments (id, name, code, description, min_purchase_frequency, min_total_spending, max_recency_days) VALUES
('seg-001', 'กลุ่มลูกค้าสั่งซื้อประจำ (Loyal Champions)', 'LOYAL_CHAMPIONS', 'สั่งซื้อมากกว่า 5 ครั้ง และมียอดซื้อรวมเกิน 5,000 บาท', 5, 5000.0000, 30)
ON CONFLICT (id) DO NOTHING;
