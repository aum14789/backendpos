-- SunPOS V6__recipe_production_schema.sql
-- Recipe, Recipe Ingredients, BOM, BOM Items, Production Order, and Order Recipe Snapshots

-- 1. Recipes (Menu Item -> Ingredients)
CREATE TABLE IF NOT EXISTS recipes (
    id VARCHAR(36) PRIMARY KEY,
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id),
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL DEFAULT 'v1.0',
    yield_quantity NUMERIC(12, 4) NOT NULL DEFAULT 1.0000,
    yield_unit VARCHAR(50) NOT NULL DEFAULT 'portion',
    is_active BOOLEAN DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recipe_ingredients (
    id VARCHAR(36) PRIMARY KEY,
    recipe_id VARCHAR(36) NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    quantity NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    waste_percentage NUMERIC(5, 2) DEFAULT 0.00
);

-- 2. Bill of Materials (BOM for Central Kitchen Production)
CREATE TABLE IF NOT EXISTS boms (
    id VARCHAR(36) PRIMARY KEY,
    finished_inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL DEFAULT 'v1.0',
    planned_output_quantity NUMERIC(12, 4) NOT NULL,
    output_unit VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bom_items (
    id VARCHAR(36) PRIMARY KEY,
    bom_id VARCHAR(36) NOT NULL REFERENCES boms(id) ON DELETE CASCADE,
    raw_inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    quantity NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL
);

-- 3. Production Orders (Central Kitchen Production)
CREATE TABLE IF NOT EXISTS production_orders (
    id VARCHAR(36) PRIMARY KEY,
    warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    bom_id VARCHAR(36) NOT NULL REFERENCES boms(id),
    production_number VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'APPROVED', -- DRAFT, APPROVED, IN_PROGRESS, COMPLETED, CANCELLED
    planned_quantity NUMERIC(12, 4) NOT NULL,
    actual_quantity NUMERIC(12, 4) DEFAULT 0.0000,
    yield_percentage NUMERIC(5, 2) DEFAULT 100.00,
    unit VARCHAR(50) NOT NULL,
    total_material_cost NUMERIC(15, 4) DEFAULT 0.0000,
    labor_cost NUMERIC(15, 4) DEFAULT 0.0000,
    packaging_cost NUMERIC(15, 4) DEFAULT 0.0000,
    overhead_cost NUMERIC(15, 4) DEFAULT 0.0000,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS production_order_items (
    id VARCHAR(36) PRIMARY KEY,
    production_order_id VARCHAR(36) NOT NULL REFERENCES production_orders(id) ON DELETE CASCADE,
    raw_inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    planned_qty NUMERIC(12, 4) NOT NULL,
    actual_qty NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(15, 4) DEFAULT 0.0000,
    total_cost NUMERIC(15, 4) DEFAULT 0.0000
);

-- 4. Order Recipe Snapshots (Historical Immutable Audit for Sales)
CREATE TABLE IF NOT EXISTS order_recipe_snapshots (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id),
    recipe_id VARCHAR(36) NOT NULL REFERENCES recipes(id),
    recipe_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_recipes_menu_item ON recipes(menu_item_id);
CREATE INDEX IF NOT EXISTS idx_boms_finished_item ON boms(finished_inventory_item_id);
CREATE INDEX IF NOT EXISTS idx_prod_orders_wh ON production_orders(warehouse_id);

-- Seed Finished Goods Inventory Item (Chicken Stock / Soup 100L)
INSERT INTO inventory_items (id, sku, name, category_name, unit, base_unit, conversion_factor, min_stock_alert) VALUES
('fg-001', 'FG-SOUP', 'น้ำซุปต้มยำสำเร็จรูป (Tom Yum Soup Base)', 'สินค้าสำเร็จรูป', 'L', 'ml', 1000.0000, 20.0000)
ON CONFLICT (id) DO NOTHING;

-- Seed Sample Recipe for Pad Kra Pao (item-001)
INSERT INTO recipes (id, menu_item_id, name, version, yield_quantity, yield_unit) VALUES
('rcp-001', 'item-001', 'สูตรผัดกะเพราหมูสับมาตรฐาน v1.0', 'v1.0', 1.0000, 'portion')
ON CONFLICT (id) DO NOTHING;

INSERT INTO recipe_ingredients (id, recipe_id, inventory_item_id, quantity, unit) VALUES
('rcpi-001', 'rcp-001', 'raw-001', 0.1000, 'kg'), -- 100g Pork
('rcpi-002', 'rcp-001', 'raw-002', 0.2000, 'kg'), -- 200g Rice
('rcpi-003', 'rcp-001', 'raw-003', 0.0100, 'L'),  -- 10ml Oil
('rcpi-004', 'rcp-001', 'raw-004', 0.0200, 'kg')  -- 20g Basil
ON CONFLICT (id) DO NOTHING;

-- Seed Sample Central Kitchen BOM for Finished Soup (fg-001)
INSERT INTO boms (id, finished_inventory_item_id, name, version, planned_output_quantity, output_unit) VALUES
('bom-001', 'fg-001', 'สูตรผลิตน้ำซุปต้มยำครัวกลาง 100 ลิตร v1.0', 'v1.0', 100.0000, 'L')
ON CONFLICT (id) DO NOTHING;

INSERT INTO bom_items (id, bom_id, raw_inventory_item_id, quantity, unit) VALUES
('bomi-001', 'bom-001', 'raw-001', 30.0000, 'kg'), -- Pork 30kg
('bomi-002', 'bom-001', 'raw-004', 5.0000, 'kg'),  -- Basil 5kg
('bomi-003', 'bom-001', 'raw-003', 2.0000, 'L')   -- Oil 2L
ON CONFLICT (id) DO NOTHING;
