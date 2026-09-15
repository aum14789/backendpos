-- V5: 2-Tier Product Concept Architecture (Global Products Tier 1 -> Brand Menu Items Tier 2)

CREATE TABLE IF NOT EXISTS global_products (
    id VARCHAR(36) NOT NULL,
    company_id VARCHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_global_products PRIMARY KEY (id),
    CONSTRAINT uq_global_products_company_code UNIQUE (company_id, code)
);

CREATE INDEX IF NOT EXISTS idx_global_products_company ON global_products(company_id);

ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS global_product_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_menu_items_global_product ON menu_items(global_product_id);

-- Backfill: create 1:1 Global Products for existing menu items by name/code
INSERT INTO global_products (id, company_id, code, name, description, created_at)
SELECT 
    'gp-' || SUBSTRING(md5(COALESCE(mi.name, 'unnamed')), 1, 24),
    'comp-001',
    COALESCE(NULLIF(mi.sku, ''), 'GP-' || SUBSTRING(md5(mi.name), 1, 6)),
    mi.name,
    mi.description,
    CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT name, MAX(sku) as sku, MAX(description) as description
    FROM menu_items
    WHERE name IS NOT NULL AND name != ''
    GROUP BY name
) mi
ON CONFLICT (id) DO NOTHING;

UPDATE menu_items mi
SET global_product_id = gp.id
FROM global_products gp
WHERE mi.name = gp.name AND (mi.global_product_id IS NULL OR mi.global_product_id = '');
