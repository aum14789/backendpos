-- =============================================================
-- V28: Inventory Branch Config + Buffet Package Recipe
-- =============================================================
-- Separates inventory configuration from POS branch settings.
-- Adds buffet package recipes for headcount-based EOD consumption.
-- =============================================================

-- 1. Inventory configuration per branch (decoupled from POS config)
CREATE TABLE inventory_branch_configs (
    id                        VARCHAR(36) PRIMARY KEY,
    branch_id                 VARCHAR(36) NOT NULL REFERENCES branches(id) UNIQUE,
    stock_deduction_mode      VARCHAR(20) NOT NULL DEFAULT 'EOD',
    buffet_consumption_mode   VARCHAR(30) NOT NULL DEFAULT 'HEADCOUNT_RECIPE',
    allow_negative_stock      BOOLEAN NOT NULL DEFAULT FALSE,
    auto_create_stock_on_sale BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by                VARCHAR(36)
);

COMMENT ON COLUMN inventory_branch_configs.stock_deduction_mode IS 'EOD = deduct at end-of-day close, REALTIME = deduct at payment time';
COMMENT ON COLUMN inventory_branch_configs.buffet_consumption_mode IS 'HEADCOUNT_RECIPE = use package recipe × headcount, ORDER_LINES_ONLY = only order line items';

-- 2. Buffet package recipe: estimated per-head consumption for self-serve buffets
--    Links a buffet promotion tier to inventory items with quantity per headcount
CREATE TABLE buffet_package_recipes (
    id                  VARCHAR(36) PRIMARY KEY,
    buffet_tier_id      VARCHAR(36) NOT NULL REFERENCES buffet_promotion_tiers(id),
    inventory_item_id   VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    quantity_per_head   DECIMAL(12,4) NOT NULL,
    unit                VARCHAR(30) NOT NULL,
    waste_percentage    DECIMAL(5,2) NOT NULL DEFAULT 0,
    notes               VARCHAR(500),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(buffet_tier_id, inventory_item_id)
);

CREATE INDEX idx_buffet_pkg_recipe_tier ON buffet_package_recipes(buffet_tier_id);
CREATE INDEX idx_buffet_pkg_recipe_item ON buffet_package_recipes(inventory_item_id);

-- 3. Permissions for inventory config management
INSERT INTO permissions (id, code, description) VALUES
('perm-invcfg-01', 'INVENTORY_CONFIG_MANAGE', 'Manage branch-level inventory configuration settings')
ON CONFLICT (id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id) VALUES
('role-01', 'perm-invcfg-01'),
('role-02', 'perm-invcfg-01')
ON CONFLICT DO NOTHING;
