-- V14__order_recipe_and_vat_hardening.sql

-- 1. Add financial_status and discount/tax snapshot columns to orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS financial_status VARCHAR(50) DEFAULT 'UNPAID';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS subtotal_amount NUMERIC(15, 4) DEFAULT 0.0000;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(15, 4) DEFAULT 0.0000;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(15, 4) DEFAULT 0.0000;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS service_charge_amount NUMERIC(15, 4) DEFAULT 0.0000;

-- 2. Add recipe snapshot to order items
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS recipe_id_snapshot VARCHAR(36);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS recipe_version_snapshot VARCHAR(50);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS combo_definition_id_snapshot VARCHAR(36);

-- 3. Promotion Allocations Table
CREATE TABLE IF NOT EXISTS order_promotion_allocations (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    order_item_id VARCHAR(36) REFERENCES order_items(id) ON DELETE CASCADE,
    promotion_id VARCHAR(36) NOT NULL,
    promotion_code VARCHAR(100) NOT NULL,
    promotion_name VARCHAR(255) NOT NULL,
    discount_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    reward_menu_item_id VARCHAR(36),
    free_quantity NUMERIC(12, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Tax Invoice Item Snapshots
CREATE TABLE IF NOT EXISTS tax_invoice_item_snapshots (
    id VARCHAR(36) PRIMARY KEY,
    tax_invoice_id VARCHAR(36) NOT NULL REFERENCES tax_invoices(id) ON DELETE CASCADE,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    order_item_id VARCHAR(36),
    item_name VARCHAR(255) NOT NULL,
    sku VARCHAR(100),
    quantity NUMERIC(12, 4) NOT NULL,
    unit_price NUMERIC(15, 4) NOT NULL,
    discount_amount NUMERIC(15, 4) DEFAULT 0.0000,
    tax_amount NUMERIC(15, 4) NOT NULL,
    net_amount NUMERIC(15, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Tax Invoice Sequence Generator Table for atomic numbering
CREATE TABLE IF NOT EXISTS tax_invoice_counters (
    branch_id VARCHAR(36) NOT NULL,
    year_val INT NOT NULL,
    current_val BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (branch_id, year_val)
);

CREATE INDEX IF NOT EXISTS idx_order_promo_alloc_order ON order_promotion_allocations (order_id);
CREATE INDEX IF NOT EXISTS idx_tax_inv_items_invoice ON tax_invoice_item_snapshots (tax_invoice_id);
