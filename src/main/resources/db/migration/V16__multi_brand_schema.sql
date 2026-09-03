-- =============================================================
-- V16: Multi-Brand Organization Architecture Schema
-- =============================================================
-- Supports multi-brand multi-branch restaurant groups:
--   - Organization / Company -> Brands -> Branches -> Devices
--   - Brand-scoped Menus, Promotions, Buffet Tiers
-- =============================================================

CREATE TABLE brands (
    id              VARCHAR(36) PRIMARY KEY,
    company_id      VARCHAR(36) NOT NULL REFERENCES companies(id),
    name            VARCHAR(200) NOT NULL,
    code            VARCHAR(50) NOT NULL UNIQUE,
    logo_url        VARCHAR(500),
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by      VARCHAR(36),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by      VARCHAR(36),
    version         BIGINT NOT NULL DEFAULT 0
);

-- Associate Branches with Brands
ALTER TABLE branches ADD COLUMN IF NOT EXISTS brand_id VARCHAR(36) REFERENCES brands(id);

-- Associate Menu Categories and Menu Items with Brands (optional brand-level catalogs)
ALTER TABLE menu_categories ADD COLUMN IF NOT EXISTS brand_id VARCHAR(36);
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS brand_id VARCHAR(36);

-- Associate Promotions with Brands
ALTER TABLE promotions ADD COLUMN IF NOT EXISTS brand_id VARCHAR(36);

-- Indexes
CREATE INDEX idx_brands_company ON brands(company_id);
CREATE INDEX idx_branches_brand ON branches(brand_id);
CREATE INDEX idx_menu_items_brand ON menu_items(brand_id);
CREATE INDEX idx_promotions_brand ON promotions(brand_id);
