-- Migration: V31__qr_ordering_schema.sql
-- Description: Cloud-First QR Code Ordering schema for customer self-ordering at tables

CREATE TABLE IF NOT EXISTS qr_orders (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL,
    table_number VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    customer_note TEXT,
    total_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    source VARCHAR(20) NOT NULL DEFAULT 'qr',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS qr_order_items (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES qr_orders(id) ON DELETE CASCADE,
    product_id VARCHAR(36) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    options TEXT, -- JSON string representing modifiers, options, etc.
    note TEXT
);

CREATE INDEX IF NOT EXISTS idx_qr_orders_branch_status_created 
ON qr_orders (branch_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_qr_orders_table_lookup 
ON qr_orders (branch_id, table_number, status);

CREATE INDEX IF NOT EXISTS idx_qr_order_items_order_id 
ON qr_order_items (order_id);
