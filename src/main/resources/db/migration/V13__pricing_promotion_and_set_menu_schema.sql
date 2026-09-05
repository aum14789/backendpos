-- SunPOS V13__pricing_promotion_and_set_menu_schema.sql

-- 1. Combo Definitions
CREATE TABLE IF NOT EXISTS combo_definitions (
    id VARCHAR(36) PRIMARY KEY,
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS combo_groups (
    id VARCHAR(36) PRIMARY KEY,
    combo_definition_id VARCHAR(36) NOT NULL REFERENCES combo_definitions(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    min_selection INT NOT NULL DEFAULT 1,
    max_selection INT NOT NULL DEFAULT 1,
    sort_order INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS combo_choices (
    id VARCHAR(36) PRIMARY KEY,
    combo_group_id VARCHAR(36) NOT NULL REFERENCES combo_groups(id) ON DELETE CASCADE,
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    price_override NUMERIC(15, 4), -- NULL means no price override (base price is used)
    surcharge NUMERIC(15, 4) DEFAULT 0.0000,
    is_free BOOLEAN DEFAULT FALSE,
    sort_order INT DEFAULT 0
);

-- 2. Order Combo Snapshot
CREATE TABLE IF NOT EXISTS order_combo_snapshots (
    id VARCHAR(36) PRIMARY KEY,
    order_item_id VARCHAR(36) NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    combo_choice_id VARCHAR(36) NOT NULL REFERENCES combo_choices(id),
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id),
    name_snapshot VARCHAR(255) NOT NULL,
    price_override_snapshot NUMERIC(15, 4) NOT NULL,
    surcharge_snapshot NUMERIC(15, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Promotions Table
CREATE TABLE IF NOT EXISTS promotions (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    promo_type VARCHAR(50) NOT NULL, -- BUY_1_GET_1, BUY_1_GET_N, BUY_N_GET_ITEM, PERCENTAGE, FIXED_AMOUNT, SET_PRICE
    priority INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    branch_id VARCHAR(36), -- NULL means applicable to all branches
    channel VARCHAR(50), -- NULL means all channels
    min_quantity NUMERIC(12, 4) DEFAULT 0.0000,
    min_amount NUMERIC(15, 4) DEFAULT 0.0000,
    discount_rate NUMERIC(5, 2) DEFAULT 0.00, -- for percentage discount
    discount_amount NUMERIC(15, 4) DEFAULT 0.0000, -- for fixed amount discount
    stacking_policy VARCHAR(50) DEFAULT 'STACKABLE', -- STACKABLE, NON_STACKABLE
    usage_limit INT,
    per_customer_limit INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS promotion_eligible_products (
    promotion_id VARCHAR(36) NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, menu_item_id)
);

CREATE TABLE IF NOT EXISTS promotion_reward_products (
    promotion_id VARCHAR(36) NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    quantity NUMERIC(12, 4) DEFAULT 1.0000,
    PRIMARY KEY (promotion_id, menu_item_id)
);

-- 4. Coupons & Redemptions
CREATE TABLE IF NOT EXISTS coupons (
    id VARCHAR(36) PRIMARY KEY,
    promotion_id VARCHAR(36) NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    code VARCHAR(100) NOT NULL UNIQUE,
    is_used BOOLEAN DEFAULT FALSE,
    max_uses INT DEFAULT 1,
    current_uses INT DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS coupon_redemption_ledgers (
    id VARCHAR(36) PRIMARY KEY,
    coupon_id VARCHAR(36) NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    customer_id VARCHAR(36) NOT NULL REFERENCES customers(id),
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id),
    redeemed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Order Applied Promotion Snapshots
CREATE TABLE IF NOT EXISTS order_applied_promotions (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    promotion_id VARCHAR(36) NOT NULL,
    promotion_code VARCHAR(100) NOT NULL,
    promotion_name VARCHAR(255) NOT NULL,
    discount_amount NUMERIC(15, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. Tax Invoices
CREATE TABLE IF NOT EXISTS tax_invoices (
    id VARCHAR(36) PRIMARY KEY,
    tax_invoice_number VARCHAR(50) NOT NULL UNIQUE,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    customer_id VARCHAR(36) REFERENCES customers(id),
    taxpayer_name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(50) NOT NULL,
    branch_number VARCHAR(20) DEFAULT '00000',
    address TEXT NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(50),
    total_net_amount NUMERIC(15, 4) NOT NULL,
    total_tax_amount NUMERIC(15, 4) NOT NULL,
    status VARCHAR(50) DEFAULT 'ISSUED', -- ISSUED, CANCELLED
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by VARCHAR(36),
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_invoice_receipts (
    tax_invoice_id VARCHAR(36) NOT NULL REFERENCES tax_invoices(id) ON DELETE CASCADE,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    PRIMARY KEY (tax_invoice_id, order_id)
);

-- 7. Scheduled Catalogs / Scheduled Sale overrides
CREATE TABLE IF NOT EXISTS scheduled_catalogs (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    scheduled_price NUMERIC(15, 4) NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) DEFAULT 'SCHEDULED', -- DRAFT, SCHEDULED, ACTIVE, EXPIRED, CANCELLED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_combo_definitions_item ON combo_definitions(menu_item_id);
CREATE INDEX IF NOT EXISTS idx_order_combo_snapshots_item ON order_combo_snapshots(order_item_id);
CREATE INDEX IF NOT EXISTS idx_promotions_duration ON promotions(start_at, end_at);
CREATE INDEX IF NOT EXISTS idx_coupons_code ON coupons(code);
CREATE INDEX IF NOT EXISTS idx_tax_invoices_customer ON tax_invoices(customer_id);
CREATE INDEX IF NOT EXISTS idx_scheduled_catalogs_item ON scheduled_catalogs(menu_item_id);
