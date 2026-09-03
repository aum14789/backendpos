-- =============================================================
-- V15: Buffet Session & Promotion Tier Schema
-- =============================================================
-- Supports buffet order type with:
--   - Per-head pricing (adults/children at different rates)
--   - Multiple promotion tiers (different menus, time limits)
--   - Brand-scoped buffet tiers
--   - Time-limited sessions
-- =============================================================

-- Buffet promotion tiers: defines pricing & time for each buffet package
CREATE TABLE buffet_promotion_tiers (
    id              VARCHAR(36) PRIMARY KEY,
    promotion_id    VARCHAR(36) NOT NULL REFERENCES promotions(id),
    name            VARCHAR(200) NOT NULL,                              -- e.g. "Standard ฿399", "Premium ฿599"
    adult_price     DECIMAL(15, 4) NOT NULL DEFAULT 0,                  -- per-head price for adults
    child_price     DECIMAL(15, 4) NOT NULL DEFAULT 0,                  -- per-head price for children
    time_limit_minutes  INT NOT NULL DEFAULT 90,                        -- session time limit
    brand_id        VARCHAR(36),                                         -- brand-scoped (NULL = all brands)
    branch_id       VARCHAR(36),                                         -- branch-scoped (NULL = all branches)
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- Menu items eligible for a specific buffet tier
CREATE TABLE buffet_tier_menu_items (
    buffet_tier_id  VARCHAR(36) NOT NULL REFERENCES buffet_promotion_tiers(id),
    menu_item_id    VARCHAR(36) NOT NULL REFERENCES menu_items(id),
    PRIMARY KEY (buffet_tier_id, menu_item_id)
);

-- Active buffet sessions linked to orders
CREATE TABLE buffet_sessions (
    id              VARCHAR(36) PRIMARY KEY,
    order_id        VARCHAR(36) NOT NULL REFERENCES orders(id),
    branch_id       VARCHAR(36) NOT NULL REFERENCES branches(id),
    buffet_tier_id  VARCHAR(36) NOT NULL REFERENCES buffet_promotion_tiers(id),
    adult_count     INT NOT NULL DEFAULT 1,
    child_count     INT NOT NULL DEFAULT 0,
    adult_price_snapshot    DECIMAL(15, 4) NOT NULL,                     -- snapshot at order time
    child_price_snapshot    DECIMAL(15, 4) NOT NULL,                     -- snapshot at order time
    time_limit_minutes      INT NOT NULL DEFAULT 90,
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at       TIMESTAMP WITH TIME ZONE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',              -- ACTIVE, TIME_WARNING, EXPIRED, CLOSED
    created_by      VARCHAR(36),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

-- Add buffet_session_id to orders table for linking
ALTER TABLE orders ADD COLUMN IF NOT EXISTS buffet_session_id VARCHAR(36);

-- Indexes
CREATE INDEX idx_buffet_sessions_order ON buffet_sessions(order_id);
CREATE INDEX idx_buffet_sessions_branch_status ON buffet_sessions(branch_id, status);
CREATE INDEX idx_buffet_tiers_promotion ON buffet_promotion_tiers(promotion_id);
CREATE INDEX idx_buffet_tiers_brand ON buffet_promotion_tiers(brand_id);
