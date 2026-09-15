-- V4: Brand-level QR Menu Settings and QR Category Sort Order
ALTER TABLE qr_order_menu_item_settings ADD COLUMN IF NOT EXISTS brand_id VARCHAR(36);
ALTER TABLE qr_order_menu_item_settings ALTER COLUMN branch_id DROP NOT NULL;
CREATE INDEX IF NOT EXISTS idx_qr_order_menu_item_settings_brand ON qr_order_menu_item_settings(brand_id);

ALTER TABLE menu_categories ADD COLUMN IF NOT EXISTS qr_sort_order INTEGER DEFAULT 0;
