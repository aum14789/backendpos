-- SunPOS V5__inventory_wac_schema.sql
-- Warehouse, Inventory Item, Stock Movement, WAC, Stock Transfer, Stock Count, and Waste schema

-- 1. Warehouses
CREATE TABLE IF NOT EXISTS warehouses (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) REFERENCES branches(id),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    is_central BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Inventory Items (Raw Ingredients & Supplies)
CREATE TABLE IF NOT EXISTS inventory_items (
    id VARCHAR(36) PRIMARY KEY,
    sku VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category_name VARCHAR(100) DEFAULT 'GENERAL',
    unit VARCHAR(50) NOT NULL, -- kg, L, pack, piece
    base_unit VARCHAR(50) NOT NULL, -- g, ml, piece
    conversion_factor NUMERIC(12, 4) NOT NULL DEFAULT 1.0000, -- e.g. 1 kg = 1000 g
    min_stock_alert NUMERIC(12, 4) DEFAULT 0.0000,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Inventory Stock (Derived Cache per Warehouse & Item)
CREATE TABLE IF NOT EXISTS inventory_stocks (
    id VARCHAR(36) PRIMARY KEY,
    warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    quantity NUMERIC(12, 4) NOT NULL DEFAULT 0.0000,
    weighted_average_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_warehouse_item UNIQUE (warehouse_id, inventory_item_id)
);

-- 4. Stock Movements (Immutable Source of Truth Ledger)
CREATE TABLE IF NOT EXISTS stock_movements (
    id VARCHAR(36) PRIMARY KEY,
    warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    quantity NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    total_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    movement_type VARCHAR(50) NOT NULL, -- OPENING, PURCHASE, SALE_CONSUMPTION, TRANSFER_OUT, TRANSFER_IN, PRODUCTION_IN, PRODUCTION_OUT, WASTE, ADJUSTMENT, STOCK_COUNT, RETURN
    reference_type VARCHAR(50),
    reference_id VARCHAR(36),
    created_by VARCHAR(36),
    business_day_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Stock Transfers
CREATE TABLE IF NOT EXISTS stock_transfers (
    id VARCHAR(36) PRIMARY KEY,
    source_warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    target_warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    transfer_number VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'REQUESTED', -- REQUESTED, APPROVED, SHIPPED, RECEIVED, CANCELLED
    shipped_at TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock_transfer_items (
    id VARCHAR(36) PRIMARY KEY,
    transfer_id VARCHAR(36) NOT NULL REFERENCES stock_transfers(id) ON DELETE CASCADE,
    inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    quantity NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000
);

-- 6. Physical Stock Counts
CREATE TABLE IF NOT EXISTS stock_counts (
    id VARCHAR(36) PRIMARY KEY,
    warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    count_number VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- DRAFT, REVIEW, APPROVED, CANCELLED
    counted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved_by VARCHAR(36),
    notes TEXT
);

CREATE TABLE IF NOT EXISTS stock_count_items (
    id VARCHAR(36) PRIMARY KEY,
    count_id VARCHAR(36) NOT NULL REFERENCES stock_counts(id) ON DELETE CASCADE,
    inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    system_qty NUMERIC(12, 4) NOT NULL,
    actual_qty NUMERIC(12, 4) NOT NULL,
    variance_qty NUMERIC(12, 4) NOT NULL,
    unit_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000
);

-- 7. Stock Waste Logs
CREATE TABLE IF NOT EXISTS stock_wastes (
    id VARCHAR(36) PRIMARY KEY,
    warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    quantity NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    total_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    reason TEXT,
    approved_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_movements_wh_item ON stock_movements(warehouse_id, inventory_item_id);
CREATE INDEX IF NOT EXISTS idx_movements_created ON stock_movements(created_at);
CREATE INDEX IF NOT EXISTS idx_transfers_source ON stock_transfers(source_warehouse_id);
CREATE INDEX IF NOT EXISTS idx_transfers_target ON stock_transfers(target_warehouse_id);

-- Seed Central Warehouse & Branch Warehouse
INSERT INTO warehouses (id, branch_id, name, code, is_central) VALUES
('wh-central', NULL, 'คลังสินค้ากลาง (HQ Central Warehouse)', 'WH-HQ-01', TRUE),
('wh-sukhumvit', 'branch-001', 'คลังสินค้าประจำสาขาสุขุมวิท', 'WH-SUK-01', FALSE)
ON CONFLICT (id) DO NOTHING;

-- Seed Inventory Items
INSERT INTO inventory_items (id, sku, name, category_name, unit, base_unit, conversion_factor, min_stock_alert) VALUES
('raw-001', 'RAW-PORK', 'เนื้อหมูสันนอกสด (Raw Pork)', 'เนื้อสัตว์', 'kg', 'g', 1000.0000, 5.0000),
('raw-002', 'RAW-RICE', 'ข้าวหอมมะลิแท้ (Jasmine Rice)', 'วัตถุดิบแห้ง', 'kg', 'g', 1000.0000, 10.0000),
('raw-003', 'RAW-OIL', 'น้ำมันพืชสำหรับทอด (Vegetable Oil)', 'เครื่องปรุง', 'L', 'ml', 1000.0000, 5.0000),
('raw-004', 'RAW-BASIL', 'ใบกะเพราสด (Fresh Holy Basil)', 'ผักสด', 'kg', 'g', 1000.0000, 1.0000)
ON CONFLICT (id) DO NOTHING;

-- Seed Initial Opening Stock & WAC
INSERT INTO inventory_stocks (id, warehouse_id, inventory_item_id, quantity, weighted_average_cost) VALUES
('stock-001', 'wh-central', 'raw-001', 50.0000, 150.0000),
('stock-002', 'wh-central', 'raw-002', 100.0000, 40.0000),
('stock-003', 'wh-sukhumvit', 'raw-001', 10.0000, 150.0000)
ON CONFLICT (id) DO NOTHING;

-- Seed Opening Movements
INSERT INTO stock_movements (id, warehouse_id, inventory_item_id, quantity, unit, unit_cost, total_cost, movement_type) VALUES
('mov-001', 'wh-central', 'raw-001', 50.0000, 'kg', 150.0000, 7500.0000, 'OPENING'),
('mov-002', 'wh-central', 'raw-002', 100.0000, 'kg', 40.0000, 4000.0000, 'OPENING'),
('mov-003', 'wh-sukhumvit', 'raw-001', 10.0000, 'kg', 150.0000, 1500.0000, 'OPENING')
ON CONFLICT (id) DO NOTHING;
