-- Per-branch visibility switches for the public QR ordering menu.
-- No row means enabled, which preserves existing menus after deployment.
CREATE TABLE IF NOT EXISTS qr_order_menu_item_settings (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    menu_item_id VARCHAR(36) NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_qr_order_menu_item_setting UNIQUE (branch_id, menu_item_id)
);

CREATE INDEX IF NOT EXISTS idx_qr_order_menu_item_settings_branch
    ON qr_order_menu_item_settings (branch_id);
