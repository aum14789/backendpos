-- V12__business_day_refactoring.sql
-- Add business_day_id column to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS business_day_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_orders_business_day ON orders (business_day_id);

-- Create inventory_close_batches table
CREATE TABLE IF NOT EXISTS inventory_close_batches (
    id VARCHAR(36) PRIMARY KEY,
    business_day_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    warehouse_id VARCHAR(36) NOT NULL REFERENCES warehouses(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PROCESSING', -- PROCESSING, COMPLETED, FAILED
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(36),
    CONSTRAINT uk_bday_warehouse UNIQUE (business_day_id, warehouse_id)
);

CREATE INDEX IF NOT EXISTS idx_close_batches_bday ON inventory_close_batches (business_day_id);