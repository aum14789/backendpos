-- Migration: V32__add_idempotency_key_to_qr_orders.sql
-- Description: Add idempotency_key to qr_orders for duplicate prevention

ALTER TABLE qr_orders 
ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_qr_orders_idempotency_key 
ON qr_orders (idempotency_key) 
WHERE idempotency_key IS NOT NULL;
