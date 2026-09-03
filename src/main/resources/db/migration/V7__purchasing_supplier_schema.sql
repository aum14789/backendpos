-- SunPOS V7__purchasing_supplier_schema.sql
-- Suppliers, Purchase Orders, Goods Receives, Purchase Returns, and Supplier Price Histories

-- 1. Suppliers
CREATE TABLE IF NOT EXISTS suppliers (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(100),
    address TEXT,
    payment_terms VARCHAR(50) DEFAULT 'Net 30', -- Net 15, Net 30, Cash On Delivery
    tax_id VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Purchase Orders (PO)
CREATE TABLE IF NOT EXISTS purchase_orders (
    id VARCHAR(36) PRIMARY KEY,
    po_number VARCHAR(50) NOT NULL UNIQUE,
    supplier_id VARCHAR(36) NOT NULL REFERENCES suppliers(id),
    warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- DRAFT, SUBMITTED, APPROVED, ORDERED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED
    total_expected_amount NUMERIC(15, 4) DEFAULT 0.0000,
    expected_date TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_by VARCHAR(36),
    approved_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id VARCHAR(36) PRIMARY KEY,
    purchase_order_id VARCHAR(36) NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    ordered_qty NUMERIC(12, 4) NOT NULL,
    received_qty NUMERIC(12, 4) DEFAULT 0.0000,
    unit VARCHAR(50) NOT NULL,
    expected_price NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    total_price NUMERIC(15, 4) NOT NULL DEFAULT 0.0000
);

-- 3. Goods Receives (GRN)
CREATE TABLE IF NOT EXISTS goods_receives (
    id VARCHAR(36) PRIMARY KEY,
    grn_number VARCHAR(50) NOT NULL UNIQUE,
    purchase_order_id VARCHAR(36) NOT NULL REFERENCES purchase_orders(id),
    warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    total_received_amount NUMERIC(15, 4) DEFAULT 0.0000,
    received_by VARCHAR(36),
    received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS goods_receive_items (
    id VARCHAR(36) PRIMARY KEY,
    goods_receive_id VARCHAR(36) NOT NULL REFERENCES goods_receives(id) ON DELETE CASCADE,
    inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    received_qty NUMERIC(12, 4) NOT NULL,
    damaged_qty NUMERIC(12, 4) DEFAULT 0.0000,
    unit VARCHAR(50) NOT NULL,
    actual_unit_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    total_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000
);

-- 4. Purchase Returns
CREATE TABLE IF NOT EXISTS purchase_returns (
    id VARCHAR(36) PRIMARY KEY,
    return_number VARCHAR(50) NOT NULL UNIQUE,
    goods_receive_id VARCHAR(36) NOT NULL REFERENCES goods_receives(id),
    supplier_id VARCHAR(36) NOT NULL REFERENCES suppliers(id),
    warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    total_return_amount NUMERIC(15, 4) DEFAULT 0.0000,
    reason TEXT,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS purchase_return_items (
    id VARCHAR(36) PRIMARY KEY,
    purchase_return_id VARCHAR(36) NOT NULL REFERENCES purchase_returns(id) ON DELETE CASCADE,
    inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    return_qty NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    total_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000
);

-- 5. Supplier Price Histories
CREATE TABLE IF NOT EXISTS supplier_price_histories (
    id VARCHAR(36) PRIMARY KEY,
    supplier_id VARCHAR(36) NOT NULL REFERENCES suppliers(id),
    inventory_item_id VARCHAR(36) NOT NULL REFERENCES inventory_items(id),
    price NUMERIC(15, 4) NOT NULL,
    effective_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_po_supplier ON purchase_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_po_status ON purchase_orders(status);
CREATE INDEX IF NOT EXISTS idx_grn_po ON goods_receives(purchase_order_id);

-- Seed Suppliers
INSERT INTO suppliers (id, code, name, contact_person, phone, email, payment_terms) VALUES
('sup-001', 'SUP-CP', 'บริษัท ซีพี เอฟเอส จำกัด (CP Foods)', 'สมชาย มีสุข', '02-777-8888', 'contact@cpfoods.com', 'Net 30'),
('sup-002', 'SUP-BETAGRO', 'บริษัท เบทาโกร จำกัด (มหาชน)', 'สมหญิง แจ่มใส', '02-555-9999', 'sales@betagro.com', 'Net 15')
ON CONFLICT (id) DO NOTHING;

-- Seed Supplier Price History
INSERT INTO supplier_price_histories (id, supplier_id, inventory_item_id, price) VALUES
('sph-001', 'sup-001', 'raw-001', 150.0000),
('sph-002', 'sup-001', 'raw-002', 40.0000)
ON CONFLICT (id) DO NOTHING;
