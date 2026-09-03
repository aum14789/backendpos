-- =============================================================
-- V24: CRM Coupon MVP Schema & Redemptions
-- =============================================================

-- 1. Alter coupons table with standalone MVP columns
ALTER TABLE coupons ALTER COLUMN promotion_id DROP NOT NULL;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS company_id VARCHAR(36) NOT NULL DEFAULT 'comp-001';
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS brand_id VARCHAR(36);
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS branch_id VARCHAR(36);
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS type VARCHAR(20) NOT NULL DEFAULT 'FIXED';
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS value NUMERIC(15, 4) NOT NULL DEFAULT 0.0000;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS min_spend NUMERIC(15, 4) NOT NULL DEFAULT 0.0000;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS max_discount NUMERIC(15, 4);
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS usage_limit_total INT;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS usage_limit_per_customer INT DEFAULT 1;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS valid_from TIMESTAMP WITH TIME ZONE;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS valid_to TIMESTAMP WITH TIME ZONE;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS version INT DEFAULT 0;

-- 2. Create coupon_redemptions table
CREATE TABLE IF NOT EXISTS coupon_redemptions (
    id VARCHAR(36) PRIMARY KEY,
    coupon_id VARCHAR(36) NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    customer_id VARCHAR(36) REFERENCES customers(id),
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id),
    discount_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    redeemed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_coupon_redemptions_coupon_order UNIQUE (coupon_id, order_id)
);

-- 3. Performance Indexes
CREATE INDEX IF NOT EXISTS idx_coupons_company_status ON coupons(company_id, status);
CREATE INDEX IF NOT EXISTS idx_coupon_redemptions_coupon_cust ON coupon_redemptions(coupon_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_coupon_redemptions_order ON coupon_redemptions(order_id);

-- 4. Backfill and Seed Standalone Demo Coupons
UPDATE coupons
SET 
    name = CASE 
        WHEN code = 'WELCOME50' THEN 'คูปองต้อนรับสมาชิกใหม่ ฿50'
        WHEN code = 'SUNVIP10' THEN 'คูปองสมาชิก VIP ลด 10%'
        ELSE COALESCE(name, code)
    END,
    type = CASE 
        WHEN code = 'SUNVIP10' THEN 'PERCENT'
        ELSE 'FIXED'
    END,
    value = CASE 
        WHEN code = 'WELCOME50' THEN 50.0000
        WHEN code = 'SUNVIP10' THEN 10.0000
        ELSE 0.0000
    END,
    min_spend = CASE 
        WHEN code = 'WELCOME50' THEN 200.0000
        WHEN code = 'SUNVIP10' THEN 300.0000
        ELSE 0.0000
    END,
    max_discount = CASE 
        WHEN code = 'SUNVIP10' THEN 100.0000
        ELSE NULL
    END,
    usage_limit_total = 1000,
    usage_limit_per_customer = 1,
    valid_from = CURRENT_TIMESTAMP - INTERVAL '30 days',
    valid_to = CURRENT_TIMESTAMP + INTERVAL '365 days',
    status = 'ACTIVE'
WHERE code IN ('WELCOME50', 'SUNVIP10');

INSERT INTO coupons (
    id, company_id, code, name, description, type, value, min_spend, max_discount,
    usage_limit_total, usage_limit_per_customer, valid_from, valid_to, status
)
VALUES (
    'coup-demo-100', 'comp-001', 'DISCOUNT100', 'คูปองส่วนลดพิเศษ ฿100', 'ส่วนลดเงินสด ฿100 เมื่อมียอดซื้อขั้นต่ำ ฿500',
    'FIXED', 100.0000, 500.0000, NULL, 500, 1,
    CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP + INTERVAL '180 days', 'ACTIVE'
)
ON CONFLICT (code) DO NOTHING;
