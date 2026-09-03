-- SunPOS V3__pos_core_schema.sql
-- Table, Floor, Zone, Menu Catalog, Modifiers, and Order schema

-- 1. Floor & Zone
CREATE TABLE IF NOT EXISTS zones (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    name VARCHAR(100) NOT NULL,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Table Type
CREATE TABLE IF NOT EXISTS table_types (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Tables
CREATE TABLE IF NOT EXISTS tables (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    zone_id VARCHAR(36) REFERENCES zones(id),
    table_type_id VARCHAR(36) REFERENCES table_types(id),
    name_number VARCHAR(50) NOT NULL,
    capacity INT DEFAULT 4,
    status VARCHAR(50) DEFAULT 'AVAILABLE', -- AVAILABLE, OCCUPIED, WAITING_PAYMENT, RESERVED, CLEANING, OUT_OF_SERVICE
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- 4. Table Sessions
CREATE TABLE IF NOT EXISTS table_sessions (
    id VARCHAR(36) PRIMARY KEY,
    table_id VARCHAR(36) NOT NULL REFERENCES tables(id),
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, CLOSED
    opened_by VARCHAR(36),
    closed_by VARCHAR(36)
);

-- 5. Menu Categories
CREATE TABLE IF NOT EXISTS menu_categories (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    sort_order INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. Menu Items
CREATE TABLE IF NOT EXISTS menu_items (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    category_id VARCHAR(36) NOT NULL REFERENCES menu_categories(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    sku VARCHAR(100),
    base_price NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    availability VARCHAR(50) DEFAULT 'AVAILABLE', -- AVAILABLE, SOLD_OUT, DISABLED
    image_url TEXT,
    sort_order INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- 7. Modifier Groups & Modifiers
CREATE TABLE IF NOT EXISTS modifier_groups (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    name VARCHAR(100) NOT NULL,
    min_selection INT DEFAULT 0,
    max_selection INT DEFAULT 1,
    is_required BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS modifiers (
    id VARCHAR(36) PRIMARY KEY,
    modifier_group_id VARCHAR(36) NOT NULL REFERENCES modifier_groups(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS menu_item_modifier_groups (
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    modifier_group_id VARCHAR(36) NOT NULL REFERENCES modifier_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (menu_item_id, modifier_group_id)
);

-- 8. Orders
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    table_id VARCHAR(36) REFERENCES tables(id),
    table_session_id VARCHAR(36) REFERENCES table_sessions(id),
    order_number VARCHAR(50) NOT NULL,
    order_type VARCHAR(50) NOT NULL, -- DINE_IN, TAKEAWAY, DELIVERY, BUFFET
    channel VARCHAR(50) NOT NULL, -- POS, QR, LINE, WEB, DELIVERY, OTHER
    status VARCHAR(50) DEFAULT 'OPEN', -- OPEN, CONFIRMED, IN_KITCHEN, READY, SERVED, COMPLETED, CANCELLED, VOIDED
    kitchen_status VARCHAR(50) DEFAULT 'NOT_SENT', -- NOT_SENT, SENT, PREPARING, READY, CANCELLED
    total_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- 9. Order Items & Order Item Modifiers
CREATE TABLE IF NOT EXISTS order_items (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id VARCHAR(36) NOT NULL,
    name_snapshot VARCHAR(255) NOT NULL,
    unit_price_snapshot NUMERIC(15, 4) NOT NULL,
    quantity NUMERIC(12, 4) NOT NULL DEFAULT 1.0000,
    notes TEXT,
    subtotal NUMERIC(15, 4) NOT NULL,
    kitchen_status VARCHAR(50) DEFAULT 'NOT_SENT',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_item_modifiers (
    id VARCHAR(36) PRIMARY KEY,
    order_item_id VARCHAR(36) NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    modifier_id VARCHAR(36) NOT NULL,
    name_snapshot VARCHAR(100) NOT NULL,
    price_snapshot NUMERIC(15, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_tables_branch ON tables(branch_id);
CREATE INDEX IF NOT EXISTS idx_table_sessions_table ON table_sessions(table_id);
CREATE INDEX IF NOT EXISTS idx_menu_items_category ON menu_items(category_id);
CREATE INDEX IF NOT EXISTS idx_orders_branch ON orders(branch_id);
CREATE INDEX IF NOT EXISTS idx_orders_table_session ON orders(table_session_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);

-- Seed Initial Floor & Zone
INSERT INTO zones (id, branch_id, name, sort_order) VALUES
('zone-001', 'branch-001', 'Main Dining Hall', 1),
('zone-002', 'branch-001', 'Outdoor Terrace', 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO table_types (id, branch_id, name, code, is_default) VALUES
('ttype-001', 'branch-001', 'Standard Dining Table', 'NORMAL', TRUE),
('ttype-002', 'branch-001', 'Buffet Table', 'BUFFET', FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tables (id, branch_id, zone_id, table_type_id, name_number, capacity, status) VALUES
('tbl-001', 'branch-001', 'zone-001', 'ttype-001', 'T-01', 4, 'AVAILABLE'),
('tbl-002', 'branch-001', 'zone-001', 'ttype-001', 'T-02', 4, 'AVAILABLE'),
('tbl-003', 'branch-001', 'zone-002', 'ttype-002', 'B-01', 6, 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;

-- Seed Menu Categories & Items
INSERT INTO menu_categories (id, branch_id, name, description, sort_order) VALUES
('cat-001', 'branch-001', 'Main Dishes', 'Popular Thai main courses', 1),
('cat-002', 'branch-001', 'Beverages', 'Refreshing drinks and teas', 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO menu_items (id, branch_id, category_id, name, description, sku, base_price, availability) VALUES
('item-001', 'branch-001', 'cat-001', 'Pad Kra Pao (Stir-fried Basil)', 'Spicy stir-fried minced pork with holy basil', 'SKU-KRAPAO', 120.0000, 'AVAILABLE'),
('item-002', 'branch-001', 'cat-001', 'Tom Yum Goong', 'Spicy and sour prawn soup', 'SKU-TOMYUM', 250.0000, 'AVAILABLE'),
('item-003', 'branch-001', 'cat-002', 'Thai Iced Tea', 'Traditional sweet Thai milk tea', 'SKU-ICEDTEA', 60.0000, 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;

-- Seed Modifier Groups & Modifiers
INSERT INTO modifier_groups (id, branch_id, name, min_selection, max_selection, is_required) VALUES
('modgrp-001', 'branch-001', 'Egg Selection', 0, 1, FALSE),
('modgrp-002', 'branch-001', 'Spiciness Level', 1, 1, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO modifiers (id, modifier_group_id, name, price) VALUES
('mod-001', 'modgrp-001', 'Fried Egg', 15.0000),
('mod-002', 'modgrp-001', 'Omelette', 20.0000),
('mod-003', 'modgrp-002', 'Mild', 0.0000),
('mod-004', 'modgrp-002', 'Medium Spicy', 0.0000),
('mod-005', 'modgrp-002', 'Thai Spicy', 0.0000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO menu_item_modifier_groups (menu_item_id, modifier_group_id) VALUES
('item-001', 'modgrp-001'),
('item-001', 'modgrp-002')
ON CONFLICT DO NOTHING;
