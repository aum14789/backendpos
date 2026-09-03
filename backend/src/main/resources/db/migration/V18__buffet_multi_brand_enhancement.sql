-- =============================================================
-- V18: Multi-Brand & Branch Buffet Promotions Schema
-- =============================================================
-- Supports brand-level and branch-specific buffet packages with:
--   - Per-head pricing (price_per_person)
--   - Configurable time limits per brand/promotion (duration_minutes)
--   - Scoped menu item eligibility per promotion
--   - Branch-scoped inheritance from Brand
-- =============================================================

CREATE TABLE buffet_promotions (
    id                  VARCHAR(36) PRIMARY KEY,
    brand_id            VARCHAR(36) NOT NULL REFERENCES brands(id),
    branch_id           VARCHAR(36) REFERENCES branches(id), -- NULL = applies to all branches of this brand
    name                VARCHAR(200) NOT NULL,
    price_per_person    DECIMAL(15, 4) NOT NULL DEFAULT 0,
    duration_minutes    INT NOT NULL DEFAULT 90,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE, ARCHIVED
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by          VARCHAR(36),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by          VARCHAR(36),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE buffet_promotion_menu_items (
    promotion_id        VARCHAR(36) NOT NULL REFERENCES buffet_promotions(id) ON DELETE CASCADE,
    menu_item_id        VARCHAR(36) NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, menu_item_id)
);

-- Indexes for high-performance lookup
CREATE INDEX idx_buffet_promotions_brand_status ON buffet_promotions(brand_id, status);
CREATE INDEX idx_buffet_promotions_branch_status ON buffet_promotions(branch_id, status);
CREATE INDEX idx_buffet_promo_menu_item ON buffet_promotion_menu_items(promotion_id);
