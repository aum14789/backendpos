-- =============================================================
-- V35: Menu Item Extended Attributes, Multi-Brand/Branch Mapping,
--      and Buffet Promotion Item Pricing (Free vs Upcharge)
-- =============================================================

-- 1. Extended attributes on menu_items
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS item_type VARCHAR(20) NOT NULL DEFAULT 'FG'; -- FG, RM, SEMI
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS special_type VARCHAR(20) DEFAULT NULL; -- 'S' (SET), NULL (NORMAL)
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS effective_date DATE DEFAULT NULL;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS expiry_date DATE DEFAULT NULL;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS is_vat_inclusive BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS vat_rate NUMERIC(5, 2) NOT NULL DEFAULT 7.00;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS allow_decimal_qty BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS cost_price NUMERIC(15, 4) DEFAULT 0.0000;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS barcode VARCHAR(100) DEFAULT NULL;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS kitchen_station VARCHAR(50) DEFAULT 'MAIN_KITCHEN';

-- 2. Multi-Brand & Multi-Branch Mapping table
CREATE TABLE IF NOT EXISTS menu_item_branches (
    id VARCHAR(36) PRIMARY KEY,
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    brand_id VARCHAR(36) NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    branch_id VARCHAR(36) REFERENCES branches(id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    price_override NUMERIC(15, 4) DEFAULT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_menu_item_branch UNIQUE (menu_item_id, branch_id)
);

CREATE INDEX IF NOT EXISTS idx_menu_item_branches_item ON menu_item_branches(menu_item_id);
CREATE INDEX IF NOT EXISTS idx_menu_item_branches_branch ON menu_item_branches(branch_id);
CREATE INDEX IF NOT EXISTS idx_menu_item_branches_brand ON menu_item_branches(brand_id);

-- Seed initial mappings from existing menu_items
INSERT INTO menu_item_branches (id, menu_item_id, brand_id, branch_id, is_active, created_at)
SELECT
    'mib-' || m.id,
    m.id,
    COALESCE(m.brand_id, (SELECT b.brand_id FROM branches b WHERE b.id = m.branch_id), (SELECT id FROM brands ORDER BY id LIMIT 1)),
    m.branch_id,
    COALESCE(m.is_active, TRUE),
    CURRENT_TIMESTAMP
FROM menu_items m
WHERE m.branch_id IS NOT NULL AND m.branch_id != ''
ON CONFLICT (menu_item_id, branch_id) DO NOTHING;

-- 3. Buffet Promotion Menu Items Pricing (Free vs Upcharge)
ALTER TABLE buffet_promotion_menu_items ADD COLUMN IF NOT EXISTS is_free BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE buffet_promotion_menu_items ADD COLUMN IF NOT EXISTS additional_price NUMERIC(15, 4) NOT NULL DEFAULT 0.0000;
