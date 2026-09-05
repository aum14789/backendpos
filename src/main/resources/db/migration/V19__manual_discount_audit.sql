-- =============================================================
-- V19: Order Manual Discount Audit Fields
-- =============================================================
-- Records cashier/manager manual discount reasons and authorizer
-- for full financial auditing and tax receipt compliance.
-- =============================================================

ALTER TABLE orders ADD COLUMN IF NOT EXISTS manual_discount_reason VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS manual_discount_authorized_by VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS manual_discount_percent DECIMAL(5, 2) DEFAULT 0;
