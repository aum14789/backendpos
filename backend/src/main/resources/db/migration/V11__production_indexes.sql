-- Migration: V11__production_indexes.sql
-- Description: Composite performance indexes for production query paths

CREATE INDEX IF NOT EXISTS idx_orders_branch_status_created 
ON orders (branch_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_stock_movements_wh_item_type 
ON stock_movements (warehouse_id, inventory_item_id, movement_type);

CREATE INDEX IF NOT EXISTS idx_inventory_stocks_wh_item 
ON inventory_stocks (warehouse_id, inventory_item_id);

CREATE INDEX IF NOT EXISTS idx_payments_order_status 
ON payment_transactions (order_id, status);

CREATE INDEX IF NOT EXISTS idx_point_ledgers_customer_created 
ON point_ledgers (customer_id, created_at);

CREATE INDEX IF NOT EXISTS idx_sync_events_event_id 
ON sync_events (event_id);
