-- SunPOS Consolidated Database Baseline Schema (V1)
-- Generated on 2026-09-11T09:38:46.247Z
-- Contains complete DDL schema and initial system permissions

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table: activation_codes
CREATE TABLE IF NOT EXISTS activation_codes (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(100) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    branch_name VARCHAR(255),
    branch_code VARCHAR(50),
    device_code VARCHAR(50) DEFAULT 'POS-01'::character varying,
    device_name VARCHAR(255),
    company_id VARCHAR(36),
    company_name VARCHAR(255),
    status VARCHAR(50) DEFAULT 'UNUSED'::character varying,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    activated_at TIMESTAMP WITH TIME ZONE,
    activated_device_id VARCHAR(36),
    created_by VARCHAR(36),
    CONSTRAINT pk_activation_codes PRIMARY KEY (id),
    CONSTRAINT activation_codes_code_key UNIQUE (code)
);

-- Table: audit_logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36),
    username VARCHAR(100),
    device_id VARCHAR(36),
    branch_id VARCHAR(36),
    action VARCHAR(100) NOT NULL,
    domain VARCHAR(50) NOT NULL,
    entity_id VARCHAR(36),
    before_state TEXT,
    after_state TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

-- Table: bom_items
CREATE TABLE IF NOT EXISTS bom_items (
    id VARCHAR(36) NOT NULL,
    bom_id VARCHAR(36) NOT NULL,
    raw_inventory_item_id VARCHAR(36) NOT NULL,
    quantity NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    CONSTRAINT pk_bom_items PRIMARY KEY (id)
);

-- Table: boms
CREATE TABLE IF NOT EXISTS boms (
    id VARCHAR(36) NOT NULL,
    finished_inventory_item_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) DEFAULT 'v1.0'::character varying NOT NULL,
    planned_output_quantity NUMERIC(12, 4) NOT NULL,
    output_unit VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_boms PRIMARY KEY (id)
);

-- Table: branches
CREATE TABLE IF NOT EXISTS branches (
    id VARCHAR(36) NOT NULL,
    company_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    address TEXT,
    phone VARCHAR(50),
    business_day_close_time VARCHAR(10) DEFAULT '02:00'::character varying,
    tax_rate NUMERIC(5, 2) DEFAULT 7.00,
    service_charge_rate NUMERIC(5, 2) DEFAULT 10.00,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36),
    version BIGINT DEFAULT 0,
    brand_id VARCHAR(36),
    open_time VARCHAR(10) DEFAULT '10:00'::character varying,
    close_time VARCHAR(10) DEFAULT '22:00'::character varying,
    ip_address VARCHAR(50),
    dyn_dns_host VARCHAR(255),
    allowed_ip_subnets TEXT,
    activation_code VARCHAR(100),
    is_qr_order_enabled BOOLEAN DEFAULT true NOT NULL,
    CONSTRAINT pk_branches PRIMARY KEY (id),
    CONSTRAINT branches_code_key UNIQUE (code)
);

-- Table: brands
CREATE TABLE IF NOT EXISTS brands (
    id VARCHAR(36) NOT NULL,
    company_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50) NOT NULL,
    logo_url VARCHAR(500),
    description TEXT,
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    created_by VARCHAR(36),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_by VARCHAR(36),
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_brands PRIMARY KEY (id),
    CONSTRAINT brands_code_key UNIQUE (code)
);

-- Table: buffet_package_recipes
CREATE TABLE IF NOT EXISTS buffet_package_recipes (
    id VARCHAR(36) NOT NULL,
    buffet_tier_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    quantity_per_head NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    waste_percentage NUMERIC(5, 2) DEFAULT 0 NOT NULL,
    notes VARCHAR(500),
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT pk_buffet_package_recipes PRIMARY KEY (id),
    CONSTRAINT buffet_package_recipes_buffet_tier_id_inventory_item_id_key UNIQUE (buffet_tier_id, inventory_item_id)
);

-- Table: buffet_promotion_menu_items
CREATE TABLE IF NOT EXISTS buffet_promotion_menu_items (
    id VARCHAR(100) NOT NULL,
    promotion_id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    is_free BOOLEAN DEFAULT true NOT NULL,
    additional_price NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    CONSTRAINT pk_buffet_promotion_menu_items PRIMARY KEY (id),
    CONSTRAINT uq_buffet_promotion_menu_items UNIQUE (promotion_id, menu_item_id)
);

-- Table: buffet_promotion_tiers
CREATE TABLE IF NOT EXISTS buffet_promotion_tiers (
    id VARCHAR(36) NOT NULL,
    promotion_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    adult_price NUMERIC(15, 4) DEFAULT 0 NOT NULL,
    child_price NUMERIC(15, 4) DEFAULT 0 NOT NULL,
    time_limit_minutes INTEGER DEFAULT 90 NOT NULL,
    brand_id VARCHAR(36),
    branch_id VARCHAR(36),
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_buffet_promotion_tiers PRIMARY KEY (id)
);

-- Table: buffet_promotions
CREATE TABLE IF NOT EXISTS buffet_promotions (
    id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36),
    name VARCHAR(200) NOT NULL,
    price_per_person NUMERIC(15, 4) DEFAULT 0 NOT NULL,
    duration_minutes INTEGER DEFAULT 90 NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    created_by VARCHAR(36),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_by VARCHAR(36),
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_buffet_promotions PRIMARY KEY (id)
);

-- Table: buffet_sessions
CREATE TABLE IF NOT EXISTS buffet_sessions (
    id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    buffet_tier_id VARCHAR(36) NOT NULL,
    adult_count INTEGER DEFAULT 1 NOT NULL,
    child_count INTEGER DEFAULT 0 NOT NULL,
    adult_price_snapshot NUMERIC(15, 4) NOT NULL,
    child_price_snapshot NUMERIC(15, 4) NOT NULL,
    time_limit_minutes INTEGER DEFAULT 90 NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_buffet_sessions PRIMARY KEY (id)
);

-- Table: buffet_tier_menu_items
CREATE TABLE IF NOT EXISTS buffet_tier_menu_items (
    id VARCHAR(100) NOT NULL,
    buffet_tier_id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_buffet_tier_menu_items PRIMARY KEY (id),
    CONSTRAINT uq_buffet_tier_menu_items UNIQUE (buffet_tier_id, menu_item_id)
);

-- Table: business_days
CREATE TABLE IF NOT EXISTS business_days (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    business_date DATE NOT NULL,
    closing_time_setting VARCHAR(10) DEFAULT '02:00'::character varying,
    opened_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) DEFAULT 'OPEN'::character varying NOT NULL,
    total_sales NUMERIC(15, 4) DEFAULT 0.0000,
    total_cash_payments NUMERIC(15, 4) DEFAULT 0.0000,
    total_non_cash_payments NUMERIC(15, 4) DEFAULT 0.0000,
    total_refunds NUMERIC(15, 4) DEFAULT 0.0000,
    closed_by VARCHAR(36),
    CONSTRAINT pk_business_days PRIMARY KEY (id),
    CONSTRAINT uk_branch_business_date UNIQUE (branch_id, business_date)
);

-- Table: cash_movements
CREATE TABLE IF NOT EXISTS cash_movements (
    id VARCHAR(36) NOT NULL,
    shift_id VARCHAR(36) NOT NULL,
    movement_type VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    reason TEXT,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cash_movements PRIMARY KEY (id)
);

-- Table: cashier_shifts
CREATE TABLE IF NOT EXISTS cashier_shifts (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) DEFAULT 'OPEN'::character varying NOT NULL,
    opening_cash NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    cash_sales NUMERIC(15, 4) DEFAULT 0.0000,
    cash_in NUMERIC(15, 4) DEFAULT 0.0000,
    cash_out NUMERIC(15, 4) DEFAULT 0.0000,
    refund_cash NUMERIC(15, 4) DEFAULT 0.0000,
    expected_cash NUMERIC(15, 4) DEFAULT 0.0000,
    actual_cash NUMERIC(15, 4) DEFAULT 0.0000,
    variance NUMERIC(15, 4) DEFAULT 0.0000,
    variance_type VARCHAR(50) DEFAULT 'ZERO'::character varying,
    closing_notes TEXT,
    CONSTRAINT pk_cashier_shifts PRIMARY KEY (id)
);

-- Table: combo_choices
CREATE TABLE IF NOT EXISTS combo_choices (
    id VARCHAR(36) NOT NULL,
    combo_group_id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    price_override NUMERIC(15, 4),
    surcharge NUMERIC(15, 4) DEFAULT 0.0000,
    is_free BOOLEAN DEFAULT false,
    sort_order INTEGER DEFAULT 0,
    quantity NUMERIC(10, 4) DEFAULT 1.0 NOT NULL,
    is_default BOOLEAN DEFAULT false NOT NULL,
    CONSTRAINT pk_combo_choices PRIMARY KEY (id)
);

-- Table: combo_definitions
CREATE TABLE IF NOT EXISTS combo_definitions (
    id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_combo_definitions PRIMARY KEY (id)
);

-- Table: combo_groups
CREATE TABLE IF NOT EXISTS combo_groups (
    id VARCHAR(36) NOT NULL,
    combo_definition_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    min_selection INTEGER DEFAULT 1 NOT NULL,
    max_selection INTEGER DEFAULT 1 NOT NULL,
    sort_order INTEGER DEFAULT 0,
    seq_order INTEGER DEFAULT 1 NOT NULL,
    CONSTRAINT pk_combo_groups PRIMARY KEY (id)
);

-- Table: companies
CREATE TABLE IF NOT EXISTS companies (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36),
    version BIGINT DEFAULT 0,
    CONSTRAINT pk_companies PRIMARY KEY (id)
);

-- Table: coupon_redemption_ledgers
CREATE TABLE IF NOT EXISTS coupon_redemption_ledgers (
    id VARCHAR(36) NOT NULL,
    coupon_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    redeemed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_coupon_redemption_ledgers PRIMARY KEY (id)
);

-- Table: coupon_redemptions
CREATE TABLE IF NOT EXISTS coupon_redemptions (
    id VARCHAR(36) NOT NULL,
    coupon_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36),
    order_id VARCHAR(36) NOT NULL,
    discount_amount NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    redeemed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_coupon_redemptions PRIMARY KEY (id),
    CONSTRAINT uq_coupon_redemptions_coupon_order UNIQUE (coupon_id, order_id)
);

-- Table: coupons
CREATE TABLE IF NOT EXISTS coupons (
    id VARCHAR(36) NOT NULL,
    promotion_id VARCHAR(36),
    code VARCHAR(100) NOT NULL,
    is_used BOOLEAN DEFAULT false,
    max_uses INTEGER DEFAULT 1,
    current_uses INTEGER DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE,
    company_id VARCHAR(36) DEFAULT 'comp-001'::character varying NOT NULL,
    brand_id VARCHAR(36),
    branch_id VARCHAR(36),
    name VARCHAR(255),
    description TEXT,
    type VARCHAR(20) DEFAULT 'FIXED'::character varying NOT NULL,
    value NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    min_spend NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    max_discount NUMERIC(15, 4),
    usage_limit_total INTEGER,
    usage_limit_per_customer INTEGER DEFAULT 1,
    valid_from TIMESTAMP WITH TIME ZONE,
    valid_to TIMESTAMP WITH TIME ZONE,
    active_days TEXT,
    active_start_time VARCHAR(10),
    active_end_time VARCHAR(10),
    status VARCHAR(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0,
    CONSTRAINT pk_coupons PRIMARY KEY (id),
    CONSTRAINT coupons_code_key UNIQUE (code)
);

-- Table: customer_identities
CREATE TABLE IF NOT EXISTS customer_identities (
    id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    identity_type VARCHAR(50) NOT NULL,
    identity_value VARCHAR(255) NOT NULL,
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    company_id VARCHAR(36),
    CONSTRAINT pk_customer_identities PRIMARY KEY (id),
    CONSTRAINT uk_identity_type_value UNIQUE (identity_type, identity_value)
);

-- Table: customer_memberships
CREATE TABLE IF NOT EXISTS customer_memberships (
    id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    membership_tier_id VARCHAR(36) NOT NULL,
    effective_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expiration_date TIMESTAMP WITH TIME ZONE,
    current_spent NUMERIC(15, 4) DEFAULT 0.0000,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer_memberships PRIMARY KEY (id)
);

-- Table: customer_segments
CREATE TABLE IF NOT EXISTS customer_segments (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    min_purchase_frequency INTEGER DEFAULT 0,
    min_total_spending NUMERIC(15, 4) DEFAULT 0.0000,
    max_recency_days INTEGER DEFAULT 365,
    favorite_category VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    CONSTRAINT pk_customer_segments PRIMARY KEY (id),
    CONSTRAINT customer_segments_code_key UNIQUE (code)
);

-- Table: customers
CREATE TABLE IF NOT EXISTS customers (
    id VARCHAR(36) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),
    gender VARCHAR(20),
    birth_date TIMESTAMP WITH TIME ZONE,
    customer_group VARCHAR(50) DEFAULT 'GENERAL'::character varying,
    primary_branch_id VARCHAR(36),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    company_id VARCHAR(36),
    display_name VARCHAR(200),
    status VARCHAR(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_customers PRIMARY KEY (id)
);

-- Table: device_capabilities
CREATE TABLE IF NOT EXISTS device_capabilities (
    id VARCHAR(36) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    capability VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT true NOT NULL,
    assigned_by VARCHAR(36),
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT pk_device_capabilities PRIMARY KEY (id),
    CONSTRAINT device_capabilities_device_id_capability_key UNIQUE (device_id, capability)
);

-- Table: device_capability_audit_logs
CREATE TABLE IF NOT EXISTS device_capability_audit_logs (
    id VARCHAR(36) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    action VARCHAR(30) NOT NULL,
    previous_capabilities TEXT,
    new_capabilities TEXT NOT NULL,
    changed_by VARCHAR(36),
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT pk_device_capability_audit_logs PRIMARY KEY (id)
);

-- Table: device_sync_states
CREATE TABLE IF NOT EXISTS device_sync_states (
    device_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    app_version VARCHAR(50) NOT NULL,
    ip_address VARCHAR(50),
    sync_status VARCHAR(50) DEFAULT 'SYNCED'::character varying NOT NULL,
    pending_outbox_count INTEGER DEFAULT 0,
    last_synced_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_device_sync_states PRIMARY KEY (device_id)
);

-- Table: devices
CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    device_name VARCHAR(255) NOT NULL,
    device_code VARCHAR(50) NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    app_version VARCHAR(50),
    last_sync_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) DEFAULT 'ACTIVE'::character varying,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36),
    version BIGINT DEFAULT 0,
    is_active BOOLEAN DEFAULT true NOT NULL,
    CONSTRAINT pk_devices PRIMARY KEY (id),
    CONSTRAINT devices_device_code_key UNIQUE (device_code)
);

-- Table: goods_receive_items
CREATE TABLE IF NOT EXISTS goods_receive_items (
    id VARCHAR(36) NOT NULL,
    goods_receive_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    received_qty NUMERIC(12, 4) NOT NULL,
    damaged_qty NUMERIC(12, 4) DEFAULT 0.0000,
    unit VARCHAR(50) NOT NULL,
    actual_unit_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    total_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    CONSTRAINT pk_goods_receive_items PRIMARY KEY (id)
);

-- Table: goods_receives
CREATE TABLE IF NOT EXISTS goods_receives (
    id VARCHAR(36) NOT NULL,
    grn_number VARCHAR(50) NOT NULL,
    purchase_order_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    total_received_amount NUMERIC(15, 4) DEFAULT 0.0000,
    received_by VARCHAR(36),
    received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_goods_receives PRIMARY KEY (id),
    CONSTRAINT goods_receives_grn_number_key UNIQUE (grn_number)
);

-- Table: inventory_branch_configs
CREATE TABLE IF NOT EXISTS inventory_branch_configs (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    stock_deduction_mode VARCHAR(20) DEFAULT 'EOD'::character varying NOT NULL,
    buffet_consumption_mode VARCHAR(30) DEFAULT 'HEADCOUNT_RECIPE'::character varying NOT NULL,
    allow_negative_stock BOOLEAN DEFAULT false NOT NULL,
    auto_create_stock_on_sale BOOLEAN DEFAULT false NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_by VARCHAR(36),
    CONSTRAINT pk_inventory_branch_configs PRIMARY KEY (id),
    CONSTRAINT inventory_branch_configs_branch_id_key UNIQUE (branch_id)
);

-- Table: inventory_close_batches
CREATE TABLE IF NOT EXISTS inventory_close_batches (
    id VARCHAR(36) NOT NULL,
    business_day_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    status VARCHAR(50) DEFAULT 'PROCESSING'::character varying NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(36),
    CONSTRAINT pk_inventory_close_batches PRIMARY KEY (id),
    CONSTRAINT uk_bday_warehouse UNIQUE (business_day_id, warehouse_id)
);

-- Table: inventory_items
CREATE TABLE IF NOT EXISTS inventory_items (
    id VARCHAR(36) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category_name VARCHAR(100) DEFAULT 'GENERAL'::character varying,
    unit VARCHAR(50) NOT NULL,
    base_unit VARCHAR(50) NOT NULL,
    conversion_factor NUMERIC(12, 4) DEFAULT 1.0000 NOT NULL,
    receiving_unit VARCHAR(50) DEFAULT 'kg',
    receiving_unit_factor NUMERIC(12, 4) DEFAULT 1000.0000,
    dispense_unit VARCHAR(50) DEFAULT 'g',
    dispense_unit_factor NUMERIC(12, 4) DEFAULT 1.0000,
    count_frequency VARCHAR(50) DEFAULT 'DAILY',
    count_frequencies TEXT,
    brand_id VARCHAR(36),
    min_stock_alert NUMERIC(12, 4) DEFAULT 0.0000,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_inventory_items PRIMARY KEY (id),
    CONSTRAINT inventory_items_sku_key UNIQUE (sku)
);

-- Table: inventory_stocks
CREATE TABLE IF NOT EXISTS inventory_stocks (
    id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    quantity NUMERIC(12, 4) DEFAULT 0.0000 NOT NULL,
    weighted_average_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_inventory_stocks PRIMARY KEY (id),
    CONSTRAINT uk_warehouse_item UNIQUE (warehouse_id, inventory_item_id)
);

-- Table: line_oa_configs
CREATE TABLE IF NOT EXISTS line_oa_configs (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36),
    channel_id VARCHAR(100) NOT NULL,
    channel_secret VARCHAR(255) NOT NULL,
    channel_access_token TEXT NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_line_oa_configs PRIMARY KEY (id),
    CONSTRAINT line_oa_configs_channel_id_key UNIQUE (channel_id)
);

-- Table: membership_tiers
CREATE TABLE IF NOT EXISTS membership_tiers (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    rank_level INTEGER DEFAULT 1 NOT NULL,
    minimum_spent NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    point_multiplier NUMERIC(5, 2) DEFAULT 1.00 NOT NULL,
    discount_percentage NUMERIC(5, 2) DEFAULT 0.00 NOT NULL,
    is_active BOOLEAN DEFAULT true,
    company_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_membership_tiers PRIMARY KEY (id),
    CONSTRAINT membership_tiers_code_key UNIQUE (code)
);

-- Table: menu_categories
CREATE TABLE IF NOT EXISTS menu_categories (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    brand_id VARCHAR(36),
    CONSTRAINT pk_menu_categories PRIMARY KEY (id)
);

-- Table: kitchen_stations
CREATE TABLE IF NOT EXISTS kitchen_stations (
    id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36),
    branch_id VARCHAR(36),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_kitchen_stations PRIMARY KEY (id),
    CONSTRAINT uq_kitchen_stations_code UNIQUE (code)
);

-- Table: menu_item_branches
CREATE TABLE IF NOT EXISTS menu_item_branches (
    id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    brand_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36),
    is_active BOOLEAN DEFAULT true NOT NULL,
    price_override NUMERIC(15, 4) DEFAULT NULL::numeric,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_menu_item_branches PRIMARY KEY (id),
    CONSTRAINT uk_menu_item_branch UNIQUE (menu_item_id, branch_id)
);

-- Table: menu_item_modifier_groups
CREATE TABLE IF NOT EXISTS menu_item_modifier_groups (
    id VARCHAR(100) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    modifier_group_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_menu_item_modifier_groups PRIMARY KEY (id),
    CONSTRAINT uq_menu_item_modifier_group UNIQUE (menu_item_id, modifier_group_id)
);

-- Table: menu_items
CREATE TABLE IF NOT EXISTS menu_items (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    category_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    sku VARCHAR(100),
    base_price NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    availability VARCHAR(50) DEFAULT 'AVAILABLE'::character varying,
    image_url TEXT,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    brand_id VARCHAR(36),
    item_type VARCHAR(20) DEFAULT 'FG'::character varying NOT NULL,
    special_type VARCHAR(20) DEFAULT NULL::character varying,
    effective_date DATE,
    expiry_date DATE,
    is_vat_inclusive BOOLEAN DEFAULT true NOT NULL,
    vat_rate NUMERIC(5, 2) DEFAULT 7.00 NOT NULL,
    allow_decimal_qty BOOLEAN DEFAULT false NOT NULL,
    cost_price NUMERIC(15, 4) DEFAULT 0.0000,
    barcode VARCHAR(100) DEFAULT NULL::character varying,
    kitchen_station VARCHAR(50) DEFAULT 'MAIN_KITCHEN'::character varying,
    CONSTRAINT pk_menu_items PRIMARY KEY (id)
);

-- Table: modifier_groups
CREATE TABLE IF NOT EXISTS modifier_groups (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    min_selection INTEGER DEFAULT 0,
    max_selection INTEGER DEFAULT 1,
    is_required BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_modifier_groups PRIMARY KEY (id)
);

-- Table: modifiers
CREATE TABLE IF NOT EXISTS modifiers (
    id VARCHAR(36) NOT NULL,
    modifier_group_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    price NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_modifiers PRIMARY KEY (id)
);

-- Table: notification_logs
CREATE TABLE IF NOT EXISTS notification_logs (
    id VARCHAR(36) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    channel VARCHAR(50) DEFAULT 'LINE'::character varying NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING'::character varying NOT NULL,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_notification_logs PRIMARY KEY (id)
);

-- Table: order_applied_promotions
CREATE TABLE IF NOT EXISTS order_applied_promotions (
    id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    promotion_id VARCHAR(36) NOT NULL,
    promotion_code VARCHAR(100) NOT NULL,
    promotion_name VARCHAR(255) NOT NULL,
    discount_amount NUMERIC(15, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_applied_promotions PRIMARY KEY (id)
);

-- Table: order_combo_snapshots
CREATE TABLE IF NOT EXISTS order_combo_snapshots (
    id VARCHAR(36) NOT NULL,
    order_item_id VARCHAR(36) NOT NULL,
    combo_choice_id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    name_snapshot VARCHAR(255) NOT NULL,
    price_override_snapshot NUMERIC(15, 4) NOT NULL,
    surcharge_snapshot NUMERIC(15, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    quantity NUMERIC(10, 4) DEFAULT 1.0 NOT NULL,
    CONSTRAINT pk_order_combo_snapshots PRIMARY KEY (id)
);

-- Table: order_item_modifiers
CREATE TABLE IF NOT EXISTS order_item_modifiers (
    id VARCHAR(36) NOT NULL,
    order_item_id VARCHAR(36) NOT NULL,
    modifier_id VARCHAR(36) NOT NULL,
    name_snapshot VARCHAR(100) NOT NULL,
    price_snapshot NUMERIC(15, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_item_modifiers PRIMARY KEY (id)
);

-- Table: order_items
CREATE TABLE IF NOT EXISTS order_items (
    id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    name_snapshot VARCHAR(255) NOT NULL,
    unit_price_snapshot NUMERIC(15, 4) NOT NULL,
    quantity NUMERIC(12, 4) DEFAULT 1.0000 NOT NULL,
    notes TEXT,
    subtotal NUMERIC(15, 4) NOT NULL,
    kitchen_status VARCHAR(50) DEFAULT 'NOT_SENT'::character varying,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    recipe_id_snapshot VARCHAR(36),
    recipe_version_snapshot VARCHAR(50),
    combo_definition_id_snapshot VARCHAR(36),
    channel VARCHAR(50) DEFAULT 'POS' NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id)
);

-- Table: order_promotion_allocations
CREATE TABLE IF NOT EXISTS order_promotion_allocations (
    id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    order_item_id VARCHAR(36),
    promotion_id VARCHAR(36) NOT NULL,
    promotion_code VARCHAR(100) NOT NULL,
    promotion_name VARCHAR(255) NOT NULL,
    discount_amount NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    reward_menu_item_id VARCHAR(36),
    free_quantity NUMERIC(12, 4) DEFAULT 0.0000 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_promotion_allocations PRIMARY KEY (id)
);

-- Table: order_recipe_snapshots
CREATE TABLE IF NOT EXISTS order_recipe_snapshots (
    id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    recipe_id VARCHAR(36) NOT NULL,
    recipe_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_recipe_snapshots PRIMARY KEY (id)
);

-- Table: orders
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    table_id VARCHAR(36),
    table_session_id VARCHAR(36),
    order_number VARCHAR(50) NOT NULL,
    order_type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'OPEN'::character varying,
    kitchen_status VARCHAR(50) DEFAULT 'NOT_SENT'::character varying,
    total_amount NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    customer_id VARCHAR(36),
    business_day_id VARCHAR(36),
    financial_status VARCHAR(50) DEFAULT 'UNPAID'::character varying,
    subtotal_amount NUMERIC(15, 4) DEFAULT 0.0000,
    discount_amount NUMERIC(15, 4) DEFAULT 0.0000,
    tax_amount NUMERIC(15, 4) DEFAULT 0.0000,
    service_charge_amount NUMERIC(15, 4) DEFAULT 0.0000,
    buffet_session_id VARCHAR(36),
    manual_discount_reason VARCHAR(255),
    manual_discount_authorized_by VARCHAR(100),
    manual_discount_percent NUMERIC(5, 2) DEFAULT 0,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

-- Table: payment_transactions
CREATE TABLE IF NOT EXISTS payment_transactions (
    id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(36),
    shift_id VARCHAR(36),
    payment_method VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    tendered_amount NUMERIC(15, 4) DEFAULT 0.0000,
    change_amount NUMERIC(15, 4) DEFAULT 0.0000,
    status VARCHAR(50) DEFAULT 'SUCCESS'::character varying NOT NULL,
    idempotency_key VARCHAR(100),
    external_ref VARCHAR(100),
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_transactions PRIMARY KEY (id),
    CONSTRAINT payment_transactions_idempotency_key_key UNIQUE (idempotency_key)
);

-- Table: permissions
CREATE TABLE IF NOT EXISTS permissions (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT permissions_code_key UNIQUE (code)
);

-- Table: point_ledgers
CREATE TABLE IF NOT EXISTS point_ledgers (
    id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    points NUMERIC(12, 4) NOT NULL,
    balance_after NUMERIC(12, 4) NOT NULL,
    reference_type VARCHAR(50),
    reference_id VARCHAR(36),
    notes TEXT,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_point_ledgers PRIMARY KEY (id)
);

-- Table: printer_menu_categories
CREATE TABLE IF NOT EXISTS printer_menu_categories (
    printer_id VARCHAR(36) NOT NULL,
    category_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_printer_menu_categories PRIMARY KEY (printer_id, category_id)
);

-- Table: printers
CREATE TABLE IF NOT EXISTS printers (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    port INTEGER DEFAULT 9100 NOT NULL,
    is_document_printer BOOLEAN DEFAULT false NOT NULL,
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT pk_printers PRIMARY KEY (id)
);

-- Table: production_order_items
CREATE TABLE IF NOT EXISTS production_order_items (
    id VARCHAR(36) NOT NULL,
    production_order_id VARCHAR(36) NOT NULL,
    raw_inventory_item_id VARCHAR(36) NOT NULL,
    planned_qty NUMERIC(12, 4) NOT NULL,
    actual_qty NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(15, 4) DEFAULT 0.0000,
    total_cost NUMERIC(15, 4) DEFAULT 0.0000,
    CONSTRAINT pk_production_order_items PRIMARY KEY (id)
);

-- Table: production_orders
CREATE TABLE IF NOT EXISTS production_orders (
    id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    bom_id VARCHAR(36) NOT NULL,
    production_number VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'APPROVED'::character varying NOT NULL,
    planned_quantity NUMERIC(12, 4) NOT NULL,
    actual_quantity NUMERIC(12, 4) DEFAULT 0.0000,
    yield_percentage NUMERIC(5, 2) DEFAULT 100.00,
    unit VARCHAR(50) NOT NULL,
    total_material_cost NUMERIC(15, 4) DEFAULT 0.0000,
    labor_cost NUMERIC(15, 4) DEFAULT 0.0000,
    packaging_cost NUMERIC(15, 4) DEFAULT 0.0000,
    overhead_cost NUMERIC(15, 4) DEFAULT 0.0000,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_production_orders PRIMARY KEY (id),
    CONSTRAINT production_orders_production_number_key UNIQUE (production_number)
);

-- Table: promotion_eligible_products
CREATE TABLE IF NOT EXISTS promotion_eligible_products (
    id VARCHAR(100) NOT NULL,
    promotion_id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_promotion_eligible_products PRIMARY KEY (id),
    CONSTRAINT uq_promotion_eligible_products UNIQUE (promotion_id, menu_item_id)
);

-- Table: promotion_reward_products
CREATE TABLE IF NOT EXISTS promotion_reward_products (
    id VARCHAR(100) NOT NULL,
    promotion_id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    quantity NUMERIC(12, 4) DEFAULT 1.0000,
    CONSTRAINT pk_promotion_reward_products PRIMARY KEY (id),
    CONSTRAINT uq_promotion_reward_products UNIQUE (promotion_id, menu_item_id)
);

-- Table: promotions
CREATE TABLE IF NOT EXISTS promotions (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    promo_type VARCHAR(50) NOT NULL,
    priority INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active_days TEXT,
    active_start_time VARCHAR(10),
    active_end_time VARCHAR(10),
    branch_id VARCHAR(36),
    channel VARCHAR(50),
    min_quantity NUMERIC(12, 4) DEFAULT 0.0000,
    min_amount NUMERIC(15, 4) DEFAULT 0.0000,
    discount_rate NUMERIC(5, 2) DEFAULT 0.00,
    discount_amount NUMERIC(15, 4) DEFAULT 0.0000,
    stacking_policy VARCHAR(50) DEFAULT 'STACKABLE'::character varying,
    usage_limit INTEGER,
    per_customer_limit INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    brand_id VARCHAR(36),
    CONSTRAINT pk_promotions PRIMARY KEY (id),
    CONSTRAINT promotions_code_key UNIQUE (code)
);

-- Table: purchase_order_items
CREATE TABLE IF NOT EXISTS purchase_order_items (
    id VARCHAR(36) NOT NULL,
    purchase_order_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    ordered_qty NUMERIC(12, 4) NOT NULL,
    received_qty NUMERIC(12, 4) DEFAULT 0.0000,
    unit VARCHAR(50) NOT NULL,
    expected_price NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    total_price NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    CONSTRAINT pk_purchase_order_items PRIMARY KEY (id)
);

-- Table: purchase_orders
CREATE TABLE IF NOT EXISTS purchase_orders (
    id VARCHAR(36) NOT NULL,
    po_number VARCHAR(50) NOT NULL,
    supplier_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    status VARCHAR(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    total_expected_amount NUMERIC(15, 4) DEFAULT 0.0000,
    expected_date TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_by VARCHAR(36),
    approved_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_purchase_orders PRIMARY KEY (id),
    CONSTRAINT purchase_orders_po_number_key UNIQUE (po_number)
);

-- Table: purchase_return_items
CREATE TABLE IF NOT EXISTS purchase_return_items (
    id VARCHAR(36) NOT NULL,
    purchase_return_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    return_qty NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    total_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    CONSTRAINT pk_purchase_return_items PRIMARY KEY (id)
);

-- Table: purchase_returns
CREATE TABLE IF NOT EXISTS purchase_returns (
    id VARCHAR(36) NOT NULL,
    return_number VARCHAR(50) NOT NULL,
    goods_receive_id VARCHAR(36) NOT NULL,
    supplier_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    total_return_amount NUMERIC(15, 4) DEFAULT 0.0000,
    reason TEXT,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_purchase_returns PRIMARY KEY (id),
    CONSTRAINT purchase_returns_return_number_key UNIQUE (return_number)
);

-- Table: qr_order_items
CREATE TABLE IF NOT EXISTS qr_order_items (
    id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER DEFAULT 1 NOT NULL,
    unit_price NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    options TEXT,
    note TEXT,
    CONSTRAINT pk_qr_order_items PRIMARY KEY (id)
);

-- Table: qr_order_menu_item_settings
CREATE TABLE IF NOT EXISTS qr_order_menu_item_settings (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    is_enabled BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_qr_order_menu_item_settings PRIMARY KEY (id),
    CONSTRAINT uk_qr_order_menu_item_setting UNIQUE (branch_id, menu_item_id)
);

-- Table: qr_orders
CREATE TABLE IF NOT EXISTS qr_orders (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    table_number VARCHAR(50) NOT NULL,
    status VARCHAR(30) DEFAULT 'pending'::character varying NOT NULL,
    customer_note TEXT,
    total_amount NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    source VARCHAR(20) DEFAULT 'qr'::character varying NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR(100),
    session_id VARCHAR(36),
    table_id VARCHAR(36),
    cloud_received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ordered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    dispatched_at TIMESTAMP WITH TIME ZONE,
    printed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_qr_orders PRIMARY KEY (id)
);

-- Table: qr_table_sessions
CREATE TABLE IF NOT EXISTS qr_table_sessions (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    table_id VARCHAR(36) NOT NULL,
    table_number VARCHAR(50) NOT NULL,
    session_token VARCHAR(64) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP WITH TIME ZONE,
    opened_by VARCHAR(36),
    expires_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_qr_table_sessions PRIMARY KEY (id)
);

-- Table: quarantined_qr_orders
CREATE TABLE IF NOT EXISTS quarantined_qr_orders (
    id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NOT NULL,
    table_number VARCHAR(32) NOT NULL,
    table_id VARCHAR(64),
    session_id VARCHAR(64),
    total_amount NUMERIC(12, 2) DEFAULT 0.00,
    customer_note TEXT,
    source VARCHAR(32) DEFAULT 'qr'::character varying,
    idempotency_key VARCHAR(128),
    ordered_at TIMESTAMP WITH TIME ZONE,
    cloud_received_at TIMESTAMP WITH TIME ZONE,
    quarantined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(64) DEFAULT 'UNDELIVERED_TIMEOUT'::character varying,
    raw_payload TEXT,
    is_acknowledged BOOLEAN DEFAULT false,
    CONSTRAINT pk_quarantined_qr_orders PRIMARY KEY (id)
);

-- Table: recipe_ingredients
CREATE TABLE IF NOT EXISTS recipe_ingredients (
    id VARCHAR(36) NOT NULL,
    recipe_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    quantity NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    waste_percentage NUMERIC(5, 2) DEFAULT 0.00,
    CONSTRAINT pk_recipe_ingredients PRIMARY KEY (id)
);

-- Table: recipes
CREATE TABLE IF NOT EXISTS recipes (
    id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) DEFAULT 'v1.0'::character varying NOT NULL,
    yield_quantity NUMERIC(12, 4) DEFAULT 1.0000 NOT NULL,
    yield_unit VARCHAR(50) DEFAULT 'portion'::character varying NOT NULL,
    is_active BOOLEAN DEFAULT true,
    start_date TIMESTAMP WITH TIME ZONE,
    end_date TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_recipes PRIMARY KEY (id)
);

-- Table: refund_transactions
CREATE TABLE IF NOT EXISTS refund_transactions (
    id VARCHAR(36) NOT NULL,
    payment_transaction_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    amount NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    reason TEXT,
    status VARCHAR(50) DEFAULT 'COMPLETED'::character varying NOT NULL,
    approved_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_refund_transactions PRIMARY KEY (id)
);

-- Table: role_permissions
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    id VARCHAR(255) DEFAULT gen_random_uuid(),
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT uq_role_permissions_id UNIQUE (id)
);

-- Table: roles
CREATE TABLE IF NOT EXISTS roles (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT roles_name_key UNIQUE (name)
);

-- Table: scheduled_catalogs
CREATE TABLE IF NOT EXISTS scheduled_catalogs (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    menu_item_id VARCHAR(36) NOT NULL,
    scheduled_price NUMERIC(15, 4) NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) DEFAULT 'SCHEDULED'::character varying,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_scheduled_catalogs PRIMARY KEY (id)
);

-- Table: stock_count_items
CREATE TABLE IF NOT EXISTS stock_count_items (
    id VARCHAR(36) NOT NULL,
    count_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    system_qty NUMERIC(12, 4) NOT NULL,
    actual_qty NUMERIC(12, 4) NOT NULL,
    variance_qty NUMERIC(12, 4) NOT NULL,
    unit_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    CONSTRAINT pk_stock_count_items PRIMARY KEY (id)
);

-- Table: stock_counts
CREATE TABLE IF NOT EXISTS stock_counts (
    id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    count_number VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    counted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved_by VARCHAR(36),
    notes TEXT,
    CONSTRAINT pk_stock_counts PRIMARY KEY (id)
);

-- Table: stock_movements
CREATE TABLE IF NOT EXISTS stock_movements (
    id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    quantity NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    total_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    movement_type VARCHAR(50) NOT NULL,
    reference_type VARCHAR(50),
    reference_id VARCHAR(36),
    created_by VARCHAR(36),
    business_day_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_movements PRIMARY KEY (id)
);

-- Table: stock_transfer_items
CREATE TABLE IF NOT EXISTS stock_transfer_items (
    id VARCHAR(36) NOT NULL,
    transfer_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    quantity NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    CONSTRAINT pk_stock_transfer_items PRIMARY KEY (id)
);

-- Table: stock_transfers
CREATE TABLE IF NOT EXISTS stock_transfers (
    id VARCHAR(36) NOT NULL,
    source_warehouse_id VARCHAR(36) NOT NULL,
    target_warehouse_id VARCHAR(36) NOT NULL,
    transfer_number VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'REQUESTED'::character varying NOT NULL,
    shipped_at TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_transfers PRIMARY KEY (id)
);

-- Table: stock_wastes
CREATE TABLE IF NOT EXISTS stock_wastes (
    id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    quantity NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    total_cost NUMERIC(15, 4) DEFAULT 0.0000 NOT NULL,
    reason TEXT,
    approved_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_wastes PRIMARY KEY (id)
);

-- Table: supplier_price_histories
CREATE TABLE IF NOT EXISTS supplier_price_histories (
    id VARCHAR(36) NOT NULL,
    supplier_id VARCHAR(36) NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    price NUMERIC(15, 4) NOT NULL,
    effective_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_supplier_price_histories PRIMARY KEY (id)
);

-- Table: suppliers
CREATE TABLE IF NOT EXISTS suppliers (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(100),
    address TEXT,
    payment_terms VARCHAR(50) DEFAULT 'Net 30'::character varying,
    tax_id VARCHAR(50),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_suppliers PRIMARY KEY (id),
    CONSTRAINT suppliers_code_key UNIQUE (code)
);

-- Table: sync_events
CREATE TABLE IF NOT EXISTS sync_events (
    event_id VARCHAR(36) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    payload TEXT NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_sync_events PRIMARY KEY (event_id)
);

-- Table: table_sessions
CREATE TABLE IF NOT EXISTS table_sessions (
    id VARCHAR(36) NOT NULL,
    table_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) DEFAULT 'ACTIVE'::character varying,
    opened_by VARCHAR(36),
    closed_by VARCHAR(36),
    CONSTRAINT pk_table_sessions PRIMARY KEY (id)
);

-- Table: table_types
CREATE TABLE IF NOT EXISTS table_types (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    is_default BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_table_types PRIMARY KEY (id)
);

-- Table: tables
CREATE TABLE IF NOT EXISTS tables (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    zone_id VARCHAR(36),
    table_type_id VARCHAR(36),
    name_number VARCHAR(50) NOT NULL,
    capacity INTEGER DEFAULT 4,
    status VARCHAR(50) DEFAULT 'AVAILABLE'::character varying,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT pk_tables PRIMARY KEY (id)
);

-- Table: tax_invoice_counters
CREATE TABLE IF NOT EXISTS tax_invoice_counters (
    id VARCHAR(100) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    year_val INTEGER NOT NULL,
    current_val BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_tax_invoice_counters PRIMARY KEY (id),
    CONSTRAINT uq_tax_invoice_counters UNIQUE (branch_id, year_val)
);

-- Table: tax_invoice_item_snapshots
CREATE TABLE IF NOT EXISTS tax_invoice_item_snapshots (
    id VARCHAR(36) NOT NULL,
    tax_invoice_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    order_item_id VARCHAR(36),
    item_name VARCHAR(255) NOT NULL,
    sku VARCHAR(100),
    quantity NUMERIC(12, 4) NOT NULL,
    unit_price NUMERIC(15, 4) NOT NULL,
    discount_amount NUMERIC(15, 4) DEFAULT 0.0000,
    tax_amount NUMERIC(15, 4) NOT NULL,
    net_amount NUMERIC(15, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tax_invoice_item_snapshots PRIMARY KEY (id)
);

-- Table: tax_invoice_receipts
CREATE TABLE IF NOT EXISTS tax_invoice_receipts (
    id VARCHAR(100) NOT NULL,
    tax_invoice_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_tax_invoice_receipts PRIMARY KEY (id),
    CONSTRAINT uq_tax_invoice_receipts UNIQUE (tax_invoice_id, order_id)
);

-- Table: tax_invoices
CREATE TABLE IF NOT EXISTS tax_invoices (
    id VARCHAR(36) NOT NULL,
    tax_invoice_number VARCHAR(50) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36),
    taxpayer_name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(50) NOT NULL,
    branch_number VARCHAR(20) DEFAULT '00000'::character varying,
    address TEXT NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(50),
    total_net_amount NUMERIC(15, 4) NOT NULL,
    total_tax_amount NUMERIC(15, 4) NOT NULL,
    status VARCHAR(50) DEFAULT 'ISSUED'::character varying,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by VARCHAR(36),
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tax_invoices PRIMARY KEY (id),
    CONSTRAINT tax_invoices_tax_invoice_number_key UNIQUE (tax_invoice_number)
);

-- Table: user_roles
CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    id VARCHAR(255) DEFAULT gen_random_uuid(),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT uq_user_roles_id UNIQUE (id)
);

-- Table: users
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) NOT NULL,
    company_id VARCHAR(36) NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    pin_code VARCHAR(255),
    assigned_modules TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36),
    version BIGINT DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT users_username_key UNIQUE (username)
);

-- Table: warehouses
CREATE TABLE IF NOT EXISTS warehouses (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    is_central BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_warehouses PRIMARY KEY (id)
);

-- Table: zones
CREATE TABLE IF NOT EXISTS zones (
    id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    zone_type VARCHAR(50) DEFAULT 'DINE_IN'::character varying,
    is_active BOOLEAN DEFAULT true,
    CONSTRAINT pk_zones PRIMARY KEY (id)
);


-- Table: units_of_measure (Inventory & Recipes)
CREATE TABLE IF NOT EXISTS units_of_measure (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) DEFAULT 'COUNT',
    is_base_unit BOOLEAN DEFAULT false,
    base_unit_code VARCHAR(50),
    conversion_factor NUMERIC(15, 4) DEFAULT 1.0000,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_units_of_measure PRIMARY KEY (id),
    CONSTRAINT uq_units_of_measure_code UNIQUE (code)
);

-- Table: navigation_settings (Backoffice ERP Dynamic Navigation)
CREATE TABLE IF NOT EXISTS navigation_settings (
    id VARCHAR(36) NOT NULL,
    company_id VARCHAR(36),
    disabled_group_ids TEXT,
    disabled_item_paths TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36),
    CONSTRAINT pk_navigation_settings PRIMARY KEY (id)
);

-- Table: recipe_ingredient_substitutes (Kitchen Production BOM Substitutes)
CREATE TABLE IF NOT EXISTS recipe_ingredient_substitutes (
    id VARCHAR(36) NOT NULL,
    recipe_ingredient_id VARCHAR(36) NOT NULL,
    priority INTEGER DEFAULT 1,
    inventory_item_id VARCHAR(36) NOT NULL,
    quantity NUMERIC(15, 4) DEFAULT 0.0000,
    unit VARCHAR(50) NOT NULL,
    waste_percentage NUMERIC(5, 2) DEFAULT 0.00,
    CONSTRAINT pk_recipe_ingredient_substitutes PRIMARY KEY (id)
);

-- Foreign Key Constraints
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'bom_items_bom_id_fkey') THEN
    ALTER TABLE bom_items ADD CONSTRAINT bom_items_bom_id_fkey FOREIGN KEY (bom_id) REFERENCES boms(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'bom_items_raw_inventory_item_id_fkey') THEN
    ALTER TABLE bom_items ADD CONSTRAINT bom_items_raw_inventory_item_id_fkey FOREIGN KEY (raw_inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'boms_finished_inventory_item_id_fkey') THEN
    ALTER TABLE boms ADD CONSTRAINT boms_finished_inventory_item_id_fkey FOREIGN KEY (finished_inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'branches_brand_id_fkey') THEN
    ALTER TABLE branches ADD CONSTRAINT branches_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES brands(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'branches_company_id_fkey') THEN
    ALTER TABLE branches ADD CONSTRAINT branches_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'brands_company_id_fkey') THEN
    ALTER TABLE brands ADD CONSTRAINT brands_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_package_recipes_buffet_tier_id_fkey') THEN
    ALTER TABLE buffet_package_recipes ADD CONSTRAINT buffet_package_recipes_buffet_tier_id_fkey FOREIGN KEY (buffet_tier_id) REFERENCES buffet_promotion_tiers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_package_recipes_inventory_item_id_fkey') THEN
    ALTER TABLE buffet_package_recipes ADD CONSTRAINT buffet_package_recipes_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_promotion_menu_items_menu_item_id_fkey') THEN
    ALTER TABLE buffet_promotion_menu_items ADD CONSTRAINT buffet_promotion_menu_items_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_promotion_menu_items_promotion_id_fkey') THEN
    ALTER TABLE buffet_promotion_menu_items ADD CONSTRAINT buffet_promotion_menu_items_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES buffet_promotions(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_promotion_tiers_promotion_id_fkey') THEN
    ALTER TABLE buffet_promotion_tiers ADD CONSTRAINT buffet_promotion_tiers_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES promotions(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_promotions_branch_id_fkey') THEN
    ALTER TABLE buffet_promotions ADD CONSTRAINT buffet_promotions_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_promotions_brand_id_fkey') THEN
    ALTER TABLE buffet_promotions ADD CONSTRAINT buffet_promotions_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES brands(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_sessions_branch_id_fkey') THEN
    ALTER TABLE buffet_sessions ADD CONSTRAINT buffet_sessions_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_sessions_buffet_tier_id_fkey') THEN
    ALTER TABLE buffet_sessions ADD CONSTRAINT buffet_sessions_buffet_tier_id_fkey FOREIGN KEY (buffet_tier_id) REFERENCES buffet_promotion_tiers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_sessions_order_id_fkey') THEN
    ALTER TABLE buffet_sessions ADD CONSTRAINT buffet_sessions_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_tier_menu_items_buffet_tier_id_fkey') THEN
    ALTER TABLE buffet_tier_menu_items ADD CONSTRAINT buffet_tier_menu_items_buffet_tier_id_fkey FOREIGN KEY (buffet_tier_id) REFERENCES buffet_promotion_tiers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'buffet_tier_menu_items_menu_item_id_fkey') THEN
    ALTER TABLE buffet_tier_menu_items ADD CONSTRAINT buffet_tier_menu_items_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'business_days_branch_id_fkey') THEN
    ALTER TABLE business_days ADD CONSTRAINT business_days_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'cash_movements_shift_id_fkey') THEN
    ALTER TABLE cash_movements ADD CONSTRAINT cash_movements_shift_id_fkey FOREIGN KEY (shift_id) REFERENCES cashier_shifts(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'cashier_shifts_branch_id_fkey') THEN
    ALTER TABLE cashier_shifts ADD CONSTRAINT cashier_shifts_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'combo_choices_combo_group_id_fkey') THEN
    ALTER TABLE combo_choices ADD CONSTRAINT combo_choices_combo_group_id_fkey FOREIGN KEY (combo_group_id) REFERENCES combo_groups(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'combo_choices_menu_item_id_fkey') THEN
    ALTER TABLE combo_choices ADD CONSTRAINT combo_choices_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'combo_definitions_menu_item_id_fkey') THEN
    ALTER TABLE combo_definitions ADD CONSTRAINT combo_definitions_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'combo_groups_combo_definition_id_fkey') THEN
    ALTER TABLE combo_groups ADD CONSTRAINT combo_groups_combo_definition_id_fkey FOREIGN KEY (combo_definition_id) REFERENCES combo_definitions(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'coupon_redemption_ledgers_coupon_id_fkey') THEN
    ALTER TABLE coupon_redemption_ledgers ADD CONSTRAINT coupon_redemption_ledgers_coupon_id_fkey FOREIGN KEY (coupon_id) REFERENCES coupons(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'coupon_redemption_ledgers_customer_id_fkey') THEN
    ALTER TABLE coupon_redemption_ledgers ADD CONSTRAINT coupon_redemption_ledgers_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES customers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'coupon_redemption_ledgers_order_id_fkey') THEN
    ALTER TABLE coupon_redemption_ledgers ADD CONSTRAINT coupon_redemption_ledgers_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'coupon_redemptions_coupon_id_fkey') THEN
    ALTER TABLE coupon_redemptions ADD CONSTRAINT coupon_redemptions_coupon_id_fkey FOREIGN KEY (coupon_id) REFERENCES coupons(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'coupon_redemptions_customer_id_fkey') THEN
    ALTER TABLE coupon_redemptions ADD CONSTRAINT coupon_redemptions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES customers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'coupon_redemptions_order_id_fkey') THEN
    ALTER TABLE coupon_redemptions ADD CONSTRAINT coupon_redemptions_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'coupons_promotion_id_fkey') THEN
    ALTER TABLE coupons ADD CONSTRAINT coupons_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES promotions(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_identities_company_id_fkey') THEN
    ALTER TABLE customer_identities ADD CONSTRAINT customer_identities_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_identities_customer_id_fkey') THEN
    ALTER TABLE customer_identities ADD CONSTRAINT customer_identities_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES customers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_memberships_customer_id_fkey') THEN
    ALTER TABLE customer_memberships ADD CONSTRAINT customer_memberships_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES customers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customer_memberships_membership_tier_id_fkey') THEN
    ALTER TABLE customer_memberships ADD CONSTRAINT customer_memberships_membership_tier_id_fkey FOREIGN KEY (membership_tier_id) REFERENCES membership_tiers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customers_company_id_fkey') THEN
    ALTER TABLE customers ADD CONSTRAINT customers_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'customers_primary_branch_id_fkey') THEN
    ALTER TABLE customers ADD CONSTRAINT customers_primary_branch_id_fkey FOREIGN KEY (primary_branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'device_capabilities_branch_id_fkey') THEN
    ALTER TABLE device_capabilities ADD CONSTRAINT device_capabilities_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'device_capabilities_device_id_fkey') THEN
    ALTER TABLE device_capabilities ADD CONSTRAINT device_capabilities_device_id_fkey FOREIGN KEY (device_id) REFERENCES devices(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'device_capability_audit_logs_branch_id_fkey') THEN
    ALTER TABLE device_capability_audit_logs ADD CONSTRAINT device_capability_audit_logs_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'device_capability_audit_logs_device_id_fkey') THEN
    ALTER TABLE device_capability_audit_logs ADD CONSTRAINT device_capability_audit_logs_device_id_fkey FOREIGN KEY (device_id) REFERENCES devices(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'device_sync_states_branch_id_fkey') THEN
    ALTER TABLE device_sync_states ADD CONSTRAINT device_sync_states_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'devices_branch_id_fkey') THEN
    ALTER TABLE devices ADD CONSTRAINT devices_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receive_items_goods_receive_id_fkey') THEN
    ALTER TABLE goods_receive_items ADD CONSTRAINT goods_receive_items_goods_receive_id_fkey FOREIGN KEY (goods_receive_id) REFERENCES goods_receives(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receive_items_inventory_item_id_fkey') THEN
    ALTER TABLE goods_receive_items ADD CONSTRAINT goods_receive_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receives_purchase_order_id_fkey') THEN
    ALTER TABLE goods_receives ADD CONSTRAINT goods_receives_purchase_order_id_fkey FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'goods_receives_warehouse_id_fkey') THEN
    ALTER TABLE goods_receives ADD CONSTRAINT goods_receives_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inventory_branch_configs_branch_id_fkey') THEN
    ALTER TABLE inventory_branch_configs ADD CONSTRAINT inventory_branch_configs_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inventory_close_batches_branch_id_fkey') THEN
    ALTER TABLE inventory_close_batches ADD CONSTRAINT inventory_close_batches_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inventory_close_batches_warehouse_id_fkey') THEN
    ALTER TABLE inventory_close_batches ADD CONSTRAINT inventory_close_batches_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inventory_stocks_inventory_item_id_fkey') THEN
    ALTER TABLE inventory_stocks ADD CONSTRAINT inventory_stocks_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'inventory_stocks_warehouse_id_fkey') THEN
    ALTER TABLE inventory_stocks ADD CONSTRAINT inventory_stocks_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'line_oa_configs_branch_id_fkey') THEN
    ALTER TABLE line_oa_configs ADD CONSTRAINT line_oa_configs_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'membership_tiers_company_id_fkey') THEN
    ALTER TABLE membership_tiers ADD CONSTRAINT membership_tiers_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'menu_categories_branch_id_fkey') THEN
    ALTER TABLE menu_categories ADD CONSTRAINT menu_categories_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'menu_item_branches_branch_id_fkey') THEN
    ALTER TABLE menu_item_branches ADD CONSTRAINT menu_item_branches_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'menu_item_branches_brand_id_fkey') THEN
    ALTER TABLE menu_item_branches ADD CONSTRAINT menu_item_branches_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES brands(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'menu_item_branches_menu_item_id_fkey') THEN
    ALTER TABLE menu_item_branches ADD CONSTRAINT menu_item_branches_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'menu_item_modifier_groups_menu_item_id_fkey') THEN
    ALTER TABLE menu_item_modifier_groups ADD CONSTRAINT menu_item_modifier_groups_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'menu_item_modifier_groups_modifier_group_id_fkey') THEN
    ALTER TABLE menu_item_modifier_groups ADD CONSTRAINT menu_item_modifier_groups_modifier_group_id_fkey FOREIGN KEY (modifier_group_id) REFERENCES modifier_groups(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'menu_items_branch_id_fkey') THEN
    ALTER TABLE menu_items ADD CONSTRAINT menu_items_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'menu_items_category_id_fkey') THEN
    ALTER TABLE menu_items ADD CONSTRAINT menu_items_category_id_fkey FOREIGN KEY (category_id) REFERENCES menu_categories(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'modifier_groups_branch_id_fkey') THEN
    ALTER TABLE modifier_groups ADD CONSTRAINT modifier_groups_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'modifiers_modifier_group_id_fkey') THEN
    ALTER TABLE modifiers ADD CONSTRAINT modifiers_modifier_group_id_fkey FOREIGN KEY (modifier_group_id) REFERENCES modifier_groups(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_applied_promotions_order_id_fkey') THEN
    ALTER TABLE order_applied_promotions ADD CONSTRAINT order_applied_promotions_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_combo_snapshots_combo_choice_id_fkey') THEN
    ALTER TABLE order_combo_snapshots ADD CONSTRAINT order_combo_snapshots_combo_choice_id_fkey FOREIGN KEY (combo_choice_id) REFERENCES combo_choices(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_combo_snapshots_menu_item_id_fkey') THEN
    ALTER TABLE order_combo_snapshots ADD CONSTRAINT order_combo_snapshots_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_combo_snapshots_order_item_id_fkey') THEN
    ALTER TABLE order_combo_snapshots ADD CONSTRAINT order_combo_snapshots_order_item_id_fkey FOREIGN KEY (order_item_id) REFERENCES order_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_item_modifiers_order_item_id_fkey') THEN
    ALTER TABLE order_item_modifiers ADD CONSTRAINT order_item_modifiers_order_item_id_fkey FOREIGN KEY (order_item_id) REFERENCES order_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_items_order_id_fkey') THEN
    ALTER TABLE order_items ADD CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_promotion_allocations_order_id_fkey') THEN
    ALTER TABLE order_promotion_allocations ADD CONSTRAINT order_promotion_allocations_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_promotion_allocations_order_item_id_fkey') THEN
    ALTER TABLE order_promotion_allocations ADD CONSTRAINT order_promotion_allocations_order_item_id_fkey FOREIGN KEY (order_item_id) REFERENCES order_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_recipe_snapshots_menu_item_id_fkey') THEN
    ALTER TABLE order_recipe_snapshots ADD CONSTRAINT order_recipe_snapshots_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_recipe_snapshots_order_id_fkey') THEN
    ALTER TABLE order_recipe_snapshots ADD CONSTRAINT order_recipe_snapshots_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'order_recipe_snapshots_recipe_id_fkey') THEN
    ALTER TABLE order_recipe_snapshots ADD CONSTRAINT order_recipe_snapshots_recipe_id_fkey FOREIGN KEY (recipe_id) REFERENCES recipes(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'orders_branch_id_fkey') THEN
    ALTER TABLE orders ADD CONSTRAINT orders_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'orders_customer_id_fkey') THEN
    ALTER TABLE orders ADD CONSTRAINT orders_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES customers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'orders_table_id_fkey') THEN
    ALTER TABLE orders ADD CONSTRAINT orders_table_id_fkey FOREIGN KEY (table_id) REFERENCES tables(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'orders_table_session_id_fkey') THEN
    ALTER TABLE orders ADD CONSTRAINT orders_table_session_id_fkey FOREIGN KEY (table_session_id) REFERENCES table_sessions(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_transactions_branch_id_fkey') THEN
    ALTER TABLE payment_transactions ADD CONSTRAINT payment_transactions_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payment_transactions_order_id_fkey') THEN
    ALTER TABLE payment_transactions ADD CONSTRAINT payment_transactions_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'point_ledgers_customer_id_fkey') THEN
    ALTER TABLE point_ledgers ADD CONSTRAINT point_ledgers_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES customers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'printer_menu_categories_category_id_fkey') THEN
    ALTER TABLE printer_menu_categories ADD CONSTRAINT printer_menu_categories_category_id_fkey FOREIGN KEY (category_id) REFERENCES menu_categories(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'printer_menu_categories_printer_id_fkey') THEN
    ALTER TABLE printer_menu_categories ADD CONSTRAINT printer_menu_categories_printer_id_fkey FOREIGN KEY (printer_id) REFERENCES printers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'printers_branch_id_fkey') THEN
    ALTER TABLE printers ADD CONSTRAINT printers_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_order_items_production_order_id_fkey') THEN
    ALTER TABLE production_order_items ADD CONSTRAINT production_order_items_production_order_id_fkey FOREIGN KEY (production_order_id) REFERENCES production_orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_order_items_raw_inventory_item_id_fkey') THEN
    ALTER TABLE production_order_items ADD CONSTRAINT production_order_items_raw_inventory_item_id_fkey FOREIGN KEY (raw_inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_orders_bom_id_fkey') THEN
    ALTER TABLE production_orders ADD CONSTRAINT production_orders_bom_id_fkey FOREIGN KEY (bom_id) REFERENCES boms(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'production_orders_warehouse_id_fkey') THEN
    ALTER TABLE production_orders ADD CONSTRAINT production_orders_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'promotion_eligible_products_menu_item_id_fkey') THEN
    ALTER TABLE promotion_eligible_products ADD CONSTRAINT promotion_eligible_products_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'promotion_eligible_products_promotion_id_fkey') THEN
    ALTER TABLE promotion_eligible_products ADD CONSTRAINT promotion_eligible_products_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES promotions(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'promotion_reward_products_menu_item_id_fkey') THEN
    ALTER TABLE promotion_reward_products ADD CONSTRAINT promotion_reward_products_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'promotion_reward_products_promotion_id_fkey') THEN
    ALTER TABLE promotion_reward_products ADD CONSTRAINT promotion_reward_products_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES promotions(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_order_items_inventory_item_id_fkey') THEN
    ALTER TABLE purchase_order_items ADD CONSTRAINT purchase_order_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_order_items_purchase_order_id_fkey') THEN
    ALTER TABLE purchase_order_items ADD CONSTRAINT purchase_order_items_purchase_order_id_fkey FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_orders_supplier_id_fkey') THEN
    ALTER TABLE purchase_orders ADD CONSTRAINT purchase_orders_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES suppliers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_orders_warehouse_id_fkey') THEN
    ALTER TABLE purchase_orders ADD CONSTRAINT purchase_orders_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_return_items_inventory_item_id_fkey') THEN
    ALTER TABLE purchase_return_items ADD CONSTRAINT purchase_return_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_return_items_purchase_return_id_fkey') THEN
    ALTER TABLE purchase_return_items ADD CONSTRAINT purchase_return_items_purchase_return_id_fkey FOREIGN KEY (purchase_return_id) REFERENCES purchase_returns(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_returns_goods_receive_id_fkey') THEN
    ALTER TABLE purchase_returns ADD CONSTRAINT purchase_returns_goods_receive_id_fkey FOREIGN KEY (goods_receive_id) REFERENCES goods_receives(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_returns_supplier_id_fkey') THEN
    ALTER TABLE purchase_returns ADD CONSTRAINT purchase_returns_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES suppliers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_returns_warehouse_id_fkey') THEN
    ALTER TABLE purchase_returns ADD CONSTRAINT purchase_returns_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'qr_order_items_order_id_fkey') THEN
    ALTER TABLE qr_order_items ADD CONSTRAINT qr_order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES qr_orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'qr_order_menu_item_settings_branch_id_fkey') THEN
    ALTER TABLE qr_order_menu_item_settings ADD CONSTRAINT qr_order_menu_item_settings_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'qr_order_menu_item_settings_menu_item_id_fkey') THEN
    ALTER TABLE qr_order_menu_item_settings ADD CONSTRAINT qr_order_menu_item_settings_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'recipe_ingredients_inventory_item_id_fkey') THEN
    ALTER TABLE recipe_ingredients ADD CONSTRAINT recipe_ingredients_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'recipe_ingredients_recipe_id_fkey') THEN
    ALTER TABLE recipe_ingredients ADD CONSTRAINT recipe_ingredients_recipe_id_fkey FOREIGN KEY (recipe_id) REFERENCES recipes(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'recipes_menu_item_id_fkey') THEN
    ALTER TABLE recipes ADD CONSTRAINT recipes_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'refund_transactions_branch_id_fkey') THEN
    ALTER TABLE refund_transactions ADD CONSTRAINT refund_transactions_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'refund_transactions_order_id_fkey') THEN
    ALTER TABLE refund_transactions ADD CONSTRAINT refund_transactions_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'refund_transactions_payment_transaction_id_fkey') THEN
    ALTER TABLE refund_transactions ADD CONSTRAINT refund_transactions_payment_transaction_id_fkey FOREIGN KEY (payment_transaction_id) REFERENCES payment_transactions(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'role_permissions_permission_id_fkey') THEN
    ALTER TABLE role_permissions ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES permissions(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'role_permissions_role_id_fkey') THEN
    ALTER TABLE role_permissions ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'scheduled_catalogs_branch_id_fkey') THEN
    ALTER TABLE scheduled_catalogs ADD CONSTRAINT scheduled_catalogs_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'scheduled_catalogs_menu_item_id_fkey') THEN
    ALTER TABLE scheduled_catalogs ADD CONSTRAINT scheduled_catalogs_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES menu_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_count_items_count_id_fkey') THEN
    ALTER TABLE stock_count_items ADD CONSTRAINT stock_count_items_count_id_fkey FOREIGN KEY (count_id) REFERENCES stock_counts(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_count_items_inventory_item_id_fkey') THEN
    ALTER TABLE stock_count_items ADD CONSTRAINT stock_count_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_counts_warehouse_id_fkey') THEN
    ALTER TABLE stock_counts ADD CONSTRAINT stock_counts_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_movements_inventory_item_id_fkey') THEN
    ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_movements_warehouse_id_fkey') THEN
    ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfer_items_inventory_item_id_fkey') THEN
    ALTER TABLE stock_transfer_items ADD CONSTRAINT stock_transfer_items_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfer_items_transfer_id_fkey') THEN
    ALTER TABLE stock_transfer_items ADD CONSTRAINT stock_transfer_items_transfer_id_fkey FOREIGN KEY (transfer_id) REFERENCES stock_transfers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfers_source_warehouse_id_fkey') THEN
    ALTER TABLE stock_transfers ADD CONSTRAINT stock_transfers_source_warehouse_id_fkey FOREIGN KEY (source_warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transfers_target_warehouse_id_fkey') THEN
    ALTER TABLE stock_transfers ADD CONSTRAINT stock_transfers_target_warehouse_id_fkey FOREIGN KEY (target_warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_wastes_inventory_item_id_fkey') THEN
    ALTER TABLE stock_wastes ADD CONSTRAINT stock_wastes_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_wastes_warehouse_id_fkey') THEN
    ALTER TABLE stock_wastes ADD CONSTRAINT stock_wastes_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'supplier_price_histories_inventory_item_id_fkey') THEN
    ALTER TABLE supplier_price_histories ADD CONSTRAINT supplier_price_histories_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'supplier_price_histories_supplier_id_fkey') THEN
    ALTER TABLE supplier_price_histories ADD CONSTRAINT supplier_price_histories_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES suppliers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'table_sessions_branch_id_fkey') THEN
    ALTER TABLE table_sessions ADD CONSTRAINT table_sessions_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'table_sessions_table_id_fkey') THEN
    ALTER TABLE table_sessions ADD CONSTRAINT table_sessions_table_id_fkey FOREIGN KEY (table_id) REFERENCES tables(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'table_types_branch_id_fkey') THEN
    ALTER TABLE table_types ADD CONSTRAINT table_types_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tables_branch_id_fkey') THEN
    ALTER TABLE tables ADD CONSTRAINT tables_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tables_table_type_id_fkey') THEN
    ALTER TABLE tables ADD CONSTRAINT tables_table_type_id_fkey FOREIGN KEY (table_type_id) REFERENCES table_types(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tables_zone_id_fkey') THEN
    ALTER TABLE tables ADD CONSTRAINT tables_zone_id_fkey FOREIGN KEY (zone_id) REFERENCES zones(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tax_invoice_item_snapshots_order_id_fkey') THEN
    ALTER TABLE tax_invoice_item_snapshots ADD CONSTRAINT tax_invoice_item_snapshots_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tax_invoice_item_snapshots_tax_invoice_id_fkey') THEN
    ALTER TABLE tax_invoice_item_snapshots ADD CONSTRAINT tax_invoice_item_snapshots_tax_invoice_id_fkey FOREIGN KEY (tax_invoice_id) REFERENCES tax_invoices(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tax_invoice_receipts_order_id_fkey') THEN
    ALTER TABLE tax_invoice_receipts ADD CONSTRAINT tax_invoice_receipts_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tax_invoice_receipts_tax_invoice_id_fkey') THEN
    ALTER TABLE tax_invoice_receipts ADD CONSTRAINT tax_invoice_receipts_tax_invoice_id_fkey FOREIGN KEY (tax_invoice_id) REFERENCES tax_invoices(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tax_invoices_branch_id_fkey') THEN
    ALTER TABLE tax_invoices ADD CONSTRAINT tax_invoices_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tax_invoices_customer_id_fkey') THEN
    ALTER TABLE tax_invoices ADD CONSTRAINT tax_invoices_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES customers(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'user_roles_role_id_fkey') THEN
    ALTER TABLE user_roles ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'user_roles_user_id_fkey') THEN
    ALTER TABLE user_roles ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'users_company_id_fkey') THEN
    ALTER TABLE users ADD CONSTRAINT users_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'warehouses_branch_id_fkey') THEN
    ALTER TABLE warehouses ADD CONSTRAINT warehouses_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'zones_branch_id_fkey') THEN
    ALTER TABLE zones ADD CONSTRAINT zones_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches(id);
  END IF;
END $$;

-- Indexes
CREATE INDEX idx_activation_codes_branch ON public.activation_codes USING btree (branch_id);
CREATE INDEX idx_activation_codes_code ON public.activation_codes USING btree (code);
CREATE INDEX idx_audit_logs_action ON public.audit_logs USING btree (action);
CREATE INDEX idx_audit_logs_user ON public.audit_logs USING btree (user_id);
CREATE INDEX idx_boms_finished_item ON public.boms USING btree (finished_inventory_item_id);
CREATE INDEX idx_branches_brand ON public.branches USING btree (brand_id);
CREATE INDEX idx_branches_company ON public.branches USING btree (company_id);
CREATE INDEX idx_brands_company ON public.brands USING btree (company_id);
CREATE INDEX idx_buffet_pkg_recipe_item ON public.buffet_package_recipes USING btree (inventory_item_id);
CREATE INDEX idx_buffet_pkg_recipe_tier ON public.buffet_package_recipes USING btree (buffet_tier_id);
CREATE INDEX idx_buffet_promo_menu_item ON public.buffet_promotion_menu_items USING btree (promotion_id);
CREATE INDEX idx_buffet_tiers_brand ON public.buffet_promotion_tiers USING btree (brand_id);
CREATE INDEX idx_buffet_tiers_promotion ON public.buffet_promotion_tiers USING btree (promotion_id);
CREATE INDEX idx_buffet_promotions_branch_status ON public.buffet_promotions USING btree (branch_id, status);
CREATE INDEX idx_buffet_promotions_brand_status ON public.buffet_promotions USING btree (brand_id, status);
CREATE INDEX idx_buffet_sessions_branch_status ON public.buffet_sessions USING btree (branch_id, status);
CREATE INDEX idx_buffet_sessions_order ON public.buffet_sessions USING btree (order_id);
CREATE INDEX idx_business_days_branch ON public.business_days USING btree (branch_id);
CREATE INDEX idx_shifts_branch_device ON public.cashier_shifts USING btree (branch_id, device_id);
CREATE INDEX idx_combo_definitions_item ON public.combo_definitions USING btree (menu_item_id);
CREATE INDEX idx_coupon_redemptions_coupon_cust ON public.coupon_redemptions USING btree (coupon_id, customer_id);
CREATE INDEX idx_coupon_redemptions_order ON public.coupon_redemptions USING btree (order_id);
CREATE INDEX idx_coupons_code ON public.coupons USING btree (code);
CREATE INDEX idx_coupons_company_status ON public.coupons USING btree (company_id, status);
CREATE INDEX idx_customer_identities ON public.customer_identities USING btree (identity_type, identity_value);
CREATE INDEX idx_customer_identities_company ON public.customer_identities USING btree (company_id, identity_type, identity_value);
CREATE INDEX idx_customer_identities_lookup ON public.customer_identities USING btree (identity_type, identity_value);
CREATE INDEX idx_customer_memberships_customer ON public.customer_memberships USING btree (customer_id);
CREATE INDEX idx_customers_company_status ON public.customers USING btree (company_id, status);
CREATE INDEX idx_customers_display_name ON public.customers USING btree (display_name);
CREATE INDEX idx_device_cap_branch ON public.device_capabilities USING btree (branch_id, capability);
CREATE INDEX idx_device_cap_branch_active ON public.device_capabilities USING btree (branch_id, capability, is_active);
CREATE INDEX idx_device_cap_device ON public.device_capabilities USING btree (device_id);
CREATE INDEX idx_device_cap_audit_branch ON public.device_capability_audit_logs USING btree (branch_id);
CREATE INDEX idx_device_cap_audit_dev ON public.device_capability_audit_logs USING btree (device_id);
CREATE INDEX idx_devices_branch ON public.devices USING btree (branch_id);
CREATE INDEX idx_grn_po ON public.goods_receives USING btree (purchase_order_id);
CREATE INDEX idx_close_batches_bday ON public.inventory_close_batches USING btree (business_day_id);
CREATE INDEX idx_inventory_stocks_wh_item ON public.inventory_stocks USING btree (warehouse_id, inventory_item_id);
CREATE INDEX idx_membership_tiers_company_rank ON public.membership_tiers USING btree (company_id, rank_level);
CREATE INDEX idx_menu_item_branches_branch ON public.menu_item_branches USING btree (branch_id);
CREATE INDEX idx_menu_item_branches_brand ON public.menu_item_branches USING btree (brand_id);
CREATE INDEX idx_menu_item_branches_item ON public.menu_item_branches USING btree (menu_item_id);
CREATE INDEX idx_menu_items_brand ON public.menu_items USING btree (brand_id);
CREATE INDEX idx_menu_items_category ON public.menu_items USING btree (category_id);
CREATE INDEX idx_notification_recipient ON public.notification_logs USING btree (recipient_id);
CREATE INDEX idx_notification_status ON public.notification_logs USING btree (status);
CREATE INDEX idx_order_combo_snapshots_item ON public.order_combo_snapshots USING btree (order_item_id);
CREATE INDEX idx_order_items_order ON public.order_items USING btree (order_id);
CREATE INDEX idx_order_promo_alloc_order ON public.order_promotion_allocations USING btree (order_id);
CREATE INDEX idx_orders_branch ON public.orders USING btree (branch_id);
CREATE INDEX idx_orders_branch_status_created ON public.orders USING btree (branch_id, status, created_at);
CREATE INDEX idx_orders_business_day ON public.orders USING btree (business_day_id);
CREATE INDEX idx_orders_customer_id ON public.orders USING btree (customer_id);
CREATE INDEX idx_orders_status ON public.orders USING btree (status);
CREATE INDEX idx_orders_table_session ON public.orders USING btree (table_session_id);
CREATE INDEX idx_payments_branch ON public.payment_transactions USING btree (branch_id);
CREATE INDEX idx_payments_order ON public.payment_transactions USING btree (order_id);
CREATE INDEX idx_payments_order_status ON public.payment_transactions USING btree (order_id, status);
CREATE INDEX idx_point_ledger_customer ON public.point_ledgers USING btree (customer_id);
CREATE INDEX idx_point_ledgers_customer_created ON public.point_ledgers USING btree (customer_id, created_at);
CREATE INDEX idx_printer_menu_categories_category ON public.printer_menu_categories USING btree (category_id);
CREATE INDEX idx_printers_branch ON public.printers USING btree (branch_id);
CREATE INDEX idx_prod_orders_wh ON public.production_orders USING btree (warehouse_id);
CREATE INDEX idx_promotions_brand ON public.promotions USING btree (brand_id);
CREATE INDEX idx_promotions_duration ON public.promotions USING btree (start_at, end_at);
CREATE INDEX idx_po_status ON public.purchase_orders USING btree (status);
CREATE INDEX idx_po_supplier ON public.purchase_orders USING btree (supplier_id);
CREATE INDEX idx_qr_order_items_order_id ON public.qr_order_items USING btree (order_id);
CREATE INDEX idx_qr_order_menu_item_settings_branch ON public.qr_order_menu_item_settings USING btree (branch_id);
CREATE INDEX idx_qr_orders_branch_status_created ON public.qr_orders USING btree (branch_id, status, created_at);
CREATE INDEX idx_qr_orders_session_id ON public.qr_orders USING btree (session_id);
CREATE INDEX idx_qr_orders_table_id ON public.qr_orders USING btree (branch_id, table_id);
CREATE INDEX idx_qr_orders_table_lookup ON public.qr_orders USING btree (branch_id, table_number, status);
CREATE INDEX idx_qr_table_sessions_branch_number_status ON public.qr_table_sessions USING btree (branch_id, table_number, status);
CREATE INDEX idx_qr_table_sessions_branch_table_status ON public.qr_table_sessions USING btree (branch_id, table_id, status);
CREATE INDEX idx_qr_table_sessions_token ON public.qr_table_sessions USING btree (session_token);
CREATE INDEX idx_quarantined_qr_orders_branch ON public.quarantined_qr_orders USING btree (branch_id);
CREATE INDEX idx_quarantined_qr_orders_created ON public.quarantined_qr_orders USING btree (quarantined_at);
CREATE INDEX idx_recipes_menu_item ON public.recipes USING btree (menu_item_id);
CREATE INDEX idx_refunds_payment ON public.refund_transactions USING btree (payment_transaction_id);
CREATE INDEX idx_scheduled_catalogs_item ON public.scheduled_catalogs USING btree (menu_item_id);
CREATE INDEX idx_movements_created ON public.stock_movements USING btree (created_at);
CREATE INDEX idx_movements_wh_item ON public.stock_movements USING btree (warehouse_id, inventory_item_id);
CREATE INDEX idx_stock_movements_wh_item_type ON public.stock_movements USING btree (warehouse_id, inventory_item_id, movement_type);
CREATE INDEX idx_transfers_source ON public.stock_transfers USING btree (source_warehouse_id);
CREATE INDEX idx_transfers_target ON public.stock_transfers USING btree (target_warehouse_id);
CREATE INDEX idx_sync_events_aggregate ON public.sync_events USING btree (aggregate_type, aggregate_id);
CREATE INDEX idx_sync_events_event_id ON public.sync_events USING btree (event_id);
CREATE INDEX idx_table_sessions_table ON public.table_sessions USING btree (table_id);
CREATE INDEX idx_tables_branch ON public.tables USING btree (branch_id);
CREATE INDEX idx_tax_inv_items_invoice ON public.tax_invoice_item_snapshots USING btree (tax_invoice_id);
CREATE INDEX idx_tax_invoices_customer ON public.tax_invoices USING btree (customer_id);
CREATE INDEX idx_users_company ON public.users USING btree (company_id);
CREATE INDEX idx_users_company_active ON public.users USING btree (company_id, is_active);

-- Essential System Master Seeds

-- 1. Default Permissions
INSERT INTO permissions (id, code, description) VALUES
  ('perm-01', 'ORDER_VIEW', 'View orders'),
  ('perm-02', 'ORDER_CREATE', 'Create and modify orders'),
  ('perm-03', 'ORDER_CANCEL', 'Cancel unbilled orders'),
  ('perm-04', 'ORDER_VOID', 'Void completed financial orders'),
  ('perm-05', 'DISCOUNT_APPLY', 'Apply standard promotions and discounts'),
  ('perm-06', 'DISCOUNT_OVERRIDE', 'Override manual discount limits'),
  ('perm-14', 'COUPON_OVERRIDE', 'Close bill with cash discount in place of a coupon outside its active days/time window (ADR 0009)'),
  ('perm-07', 'PAYMENT_REFUND', 'Process full or partial payment refunds'),
  ('perm-08', 'STOCK_ADJUST', 'Perform stock adjustments and counts'),
  ('perm-09', 'STOCK_TRANSFER', 'Initiate and receive warehouse stock transfers'),
  ('perm-10', 'PURCHASE_APPROVE', 'Approve purchase orders and goods receiving'),
  ('perm-11', 'PROMOTION_MANAGE', 'Create and edit promotion rules and coupons'),
  ('perm-12', 'USER_MANAGE', 'Manage users, PIN codes, and role assignments'),
  ('perm-13', 'REPORT_VIEW', 'Access sales, financial, and inventory reports'),
  ('perm-14', 'ORGANIZATION_MANAGE', 'Manage companies, branches, and POS devices'),
  ('perm-15', 'MENU_MANAGE', 'Manage menu items, categories, and prices'),
  ('perm-16', 'TABLE_MANAGE', 'Manage table layout and zones'),
  ('perm-17', 'SHIFT_OPEN', 'Open cashier shift'),
  ('perm-18', 'SHIFT_CLOSE', 'Close cashier shift'),
  ('perm-19', 'PAYMENT_PROCESS', 'Process payments'),
  ('perm-20', 'INVENTORY_CONFIG_MANAGE', 'Configure inventory settings'),
  ('perm-21', 'COUPON_MANAGE', 'Manage coupons and vouchers'),
  ('perm-22', 'crm.coupon.manage', 'CRM Coupon management'),
  ('perm-23', 'POS_CONFIG_MANAGE', 'Manage POS devices and printers')
ON CONFLICT (id) DO NOTHING;

-- 2. System Roles
INSERT INTO roles (id, name, description) VALUES 
  ('role-01', 'ROLE_SUPER_ADMIN', 'Super Administrator with full platform access'),
  ('role-02', 'ROLE_BRANCH_MANAGER', 'Branch Manager with management privileges'),
  ('role-03', 'ROLE_SUPERVISOR', 'Shift Supervisor with discount override privileges'),
  ('role-04', 'ROLE_CASHIER', 'Frontline Cashier for POS operations'),
  ('role-05', 'ROLE_KITCHEN_STAFF', 'Kitchen Display and order preparation staff')
ON CONFLICT (id) DO NOTHING;

-- 3. Grant Super Admin All Permissions
INSERT INTO role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), 'role-01', id FROM permissions
ON CONFLICT DO NOTHING;

-- Branch Manager Permissions
INSERT INTO role_permissions (id, role_id, permission_id) VALUES
  (gen_random_uuid(), 'role-02', 'perm-01'), (gen_random_uuid(), 'role-02', 'perm-02'), (gen_random_uuid(), 'role-02', 'perm-03'), (gen_random_uuid(), 'role-02', 'perm-04'),
  (gen_random_uuid(), 'role-02', 'perm-05'), (gen_random_uuid(), 'role-02', 'perm-06'), (gen_random_uuid(), 'role-02', 'perm-07'), (gen_random_uuid(), 'role-02', 'perm-08'),
  (gen_random_uuid(), 'role-02', 'perm-09'), (gen_random_uuid(), 'role-02', 'perm-11'), (gen_random_uuid(), 'role-02', 'perm-12'), (gen_random_uuid(), 'role-02', 'perm-13'),
  (gen_random_uuid(), 'role-02', 'perm-15'), (gen_random_uuid(), 'role-02', 'perm-16'), (gen_random_uuid(), 'role-02', 'perm-17'), (gen_random_uuid(), 'role-02', 'perm-18'),
  (gen_random_uuid(), 'role-02', 'perm-19'), (gen_random_uuid(), 'role-02', 'perm-20'), (gen_random_uuid(), 'role-02', 'perm-21'), (gen_random_uuid(), 'role-02', 'perm-22'),
  (gen_random_uuid(), 'role-02', 'perm-23')
ON CONFLICT DO NOTHING;

-- Cashier Permissions
INSERT INTO role_permissions (id, role_id, permission_id) VALUES
  (gen_random_uuid(), 'role-04', 'perm-01'), (gen_random_uuid(), 'role-04', 'perm-02'), (gen_random_uuid(), 'role-04', 'perm-03'), (gen_random_uuid(), 'role-04', 'perm-05'),
  (gen_random_uuid(), 'role-04', 'perm-17'), (gen_random_uuid(), 'role-04', 'perm-18'), (gen_random_uuid(), 'role-04', 'perm-19')
ON CONFLICT DO NOTHING;

-- Kitchen Permissions
INSERT INTO role_permissions (id, role_id, permission_id) VALUES
  (gen_random_uuid(), 'role-05', 'perm-01')
ON CONFLICT DO NOTHING;

-- 4. Default Company
INSERT INTO companies (id, name, tax_id)
VALUES ('comp-001', 'SunPOS Restaurant Group Co., Ltd.', '0105560000001')
ON CONFLICT (id) DO NOTHING;

-- 5. Default Brands
INSERT INTO brands (id, company_id, name, code, is_active)
VALUES 
  ('brand-001', 'comp-001', 'SunPOS Shabu & Grill', 'SHABU', true),
  ('brand-002', 'comp-001', 'SunPOS Japanese Dining', 'JAPAN', true),
  ('brand-003', 'comp-001', 'SunPOS Cafe & Bakery', 'CAFE', true)
ON CONFLICT (id) DO NOTHING;

-- 6. Default Branches
INSERT INTO branches (id, company_id, brand_id, name, code, address, phone, business_day_close_time, tax_rate, service_charge_rate, is_active)
VALUES 
  ('branch-001', 'comp-001', 'brand-001', 'สาขาใหญ่ สยามสแควร์ (Siam Flagship)', 'HQ-01', 'Siam Square One, Bangkok', '02-123-4567', '02:00', 7.00, 10.00, true),
  ('branch-002', 'comp-001', 'brand-001', 'สาขา เซ็นทรัลลาดพร้าว (Ladprao)', 'LP-02', 'Central Ladprao, Bangkok', '02-987-6543', '02:00', 7.00, 10.00, true)
ON CONFLICT (id) DO NOTHING;

-- 7. Default Warehouses
INSERT INTO warehouses (id, branch_id, name, code, is_central, is_active)
VALUES 
  ('wh-central', 'branch-001', 'คลังสินค้ากลาง (Central Kitchen)', 'WH-CENTRAL', true, true),
  ('wh-branch-001', 'branch-001', 'คลังประจำสาขา สยามสแควร์', 'WH-B01', false, true)
ON CONFLICT (id) DO NOTHING;

-- 8. Default Users (admin, manager01, cashier01)
-- BCrypt password: password ($2a$10$i50/pAXDdCj2u43dB7CHgezdtc4f0/DWMmVdVvlMHR/AhQwXgQFaa)
-- PIN: 1234 ($2a$10$3n3nWX3a7salqcVriL.2.eVjGCbysLBhi0ReTThl26wy8IY8X5JCO)
INSERT INTO users (id, company_id, username, password_hash, pin_code, assigned_modules, full_name, email, phone, is_active)
VALUES 
  ('usr-001', 'comp-001', 'admin', '$2a$10$i50/pAXDdCj2u43dB7CHgezdtc4f0/DWMmVdVvlMHR/AhQwXgQFaa', '$2a$10$3n3nWX3a7salqcVriL.2.eVjGCbysLBhi0ReTThl26wy8IY8X5JCO', 'REPORTS,STORE_OPERATIONS,MENU_PROMOTIONS,INVENTORY_PURCHASING,KITCHEN_PRODUCTION,CRM_LOYALTY,ORG_SETTINGS', 'System Administrator', 'admin@sunpos.com', '081-999-8888', true),
  ('usr-002', 'comp-001', 'manager01', '$2a$10$i50/pAXDdCj2u43dB7CHgezdtc4f0/DWMmVdVvlMHR/AhQwXgQFaa', '$2a$10$H6N2ozIESJ4zMS23CpQuoO9Dfh6UH68ehZiak7fCaVomeX9jCqTru', 'REPORTS,STORE_OPERATIONS,MENU_PROMOTIONS,INVENTORY_PURCHASING,KITCHEN_PRODUCTION,CRM_LOYALTY,ORG_SETTINGS', 'Branch Manager 01', 'manager@sunpos.com', '081-999-7777', true),
  ('usr-003', 'comp-001', 'cashier01', '$2a$10$i50/pAXDdCj2u43dB7CHgezdtc4f0/DWMmVdVvlMHR/AhQwXgQFaa', '$2a$10$3n3nWX3a7salqcVriL.2.eVjGCbysLBhi0ReTThl26wy8IY8X5JCO', 'STORE_OPERATIONS', 'Cashier 01', 'cashier@sunpos.com', '081-999-6666', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (id, user_id, role_id)
VALUES 
  (gen_random_uuid(), 'usr-001', 'role-01'),
  (gen_random_uuid(), 'usr-002', 'role-02'),
  (gen_random_uuid(), 'usr-003', 'role-04')
ON CONFLICT DO NOTHING;

-- 9. Default Device
INSERT INTO devices (id, branch_id, device_name, device_code, device_type, app_version, status, is_active)
VALUES 
  ('dev-001', 'branch-001', 'Main Cashier POS 01', 'POS-MAIN-01', 'POS_MAIN', '1.0.0', 'ACTIVE', true)
ON CONFLICT (id) DO NOTHING;


-- 10. Table Types (Baseline Master Data)
INSERT INTO table_types (id, branch_id, name, code, is_default, created_at)
VALUES 
  ('type-std', 'branch-001', 'โต๊ะมาตรฐาน (Standard Table)', 'STD', true, CURRENT_TIMESTAMP),
  ('type-vip', 'branch-001', 'โต๊ะ VIP (VIP Room)', 'VIP', false, CURRENT_TIMESTAMP),
  ('type-bar', 'branch-001', 'เคาน์เตอร์บาร์ (Bar Counter)', 'BAR', false, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 11. Restaurant Zones
INSERT INTO zones (id, branch_id, name, zone_type, sort_order, is_active, created_at)
VALUES 
  ('zone-01', 'branch-001', 'บุฟเฟ่ต์ (Buffet Zone)', 'BUFFET', 1, true, CURRENT_TIMESTAMP),
  ('zone-02', 'branch-001', 'หน้าร้าน / A La Carte', 'DINE_IN', 2, true, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 12. Restaurant Tables
INSERT INTO tables (id, branch_id, zone_id, table_type_id, name_number, capacity, status, is_active, created_at, updated_at)
VALUES 
  ('tbl-a01', 'branch-001', 'zone-01', 'type-std', 'A01', 4, 'AVAILABLE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('tbl-a02', 'branch-001', 'zone-01', 'type-std', 'A02', 4, 'AVAILABLE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('tbl-a03', 'branch-001', 'zone-01', 'type-std', 'A03', 4, 'AVAILABLE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('tbl-a04', 'branch-001', 'zone-01', 'type-std', 'A04', 4, 'AVAILABLE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('tbl-b01', 'branch-001', 'zone-02', 'type-std', 'B01', 2, 'AVAILABLE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('tbl-b02', 'branch-001', 'zone-02', 'type-std', 'B02', 2, 'AVAILABLE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('tbl-b03', 'branch-001', 'zone-02', 'type-std', 'B03', 4, 'AVAILABLE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('tbl-b04', 'branch-001', 'zone-02', 'type-std', 'B04', 6, 'AVAILABLE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 13. Units of Measure (Inventory & Recipes)
INSERT INTO units_of_measure (id, code, name, category, is_base_unit, conversion_factor, is_active, created_at)
VALUES 
  ('uom-kg', 'KG', 'กิโลกรัม (Kilogram)', 'WEIGHT', true, 1.0000, true, CURRENT_TIMESTAMP),
  ('uom-g', 'G', 'กรัม (Gram)', 'WEIGHT', false, 0.0010, true, CURRENT_TIMESTAMP),
  ('uom-l', 'L', 'ลิตร (Liter)', 'VOLUME', true, 1.0000, true, CURRENT_TIMESTAMP),
  ('uom-ml', 'ML', 'มิลลิลิตร (Milliliter)', 'VOLUME', false, 0.0010, true, CURRENT_TIMESTAMP),
  ('uom-pcs', 'PCS', 'ชิ้น / จาน (Piece)', 'COUNT', true, 1.0000, true, CURRENT_TIMESTAMP),
  ('uom-box', 'BOX', 'กล่อง (Box)', 'COUNT', false, 12.0000, true, CURRENT_TIMESTAMP),
  ('uom-pack', 'PACK', 'แพ็ค (Pack)', 'COUNT', false, 10.0000, true, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 14. Navigation Settings (Default ERP Navigation Config)
INSERT INTO navigation_settings (id, company_id, disabled_group_ids, disabled_item_paths, updated_at)
VALUES ('default_nav_setting', 'comp-001', '', '', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 15. Activation Codes (Multi-Branch POS Activation Engine - ADR 0012)
INSERT INTO activation_codes (
    id, code, branch_id, branch_name, branch_code, device_code, device_name,
    company_id, company_name, status, created_at, expires_at
) VALUES 
(
    'act-branch-001-01', 'SUN-HQ01-POS01', 'branch-001', 'สาขาใหญ่ สยามสแควร์ (Siam Flagship)', 'HQ-01', 'POS-01', 'Main POS Terminal (Siam Flagship)',
    'comp-001', 'SunPOS Restaurant Group Co., Ltd.', 'UNUSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '10 years'
),
(
    'act-branch-002-01', 'SUN-LP02-POS01', 'branch-002', 'สาขา เซ็นทรัลลาดพร้าว (Ladprao)', 'LP-02', 'POS-01', 'Main POS Terminal (Ladprao)',
    'comp-001', 'SunPOS Restaurant Group Co., Ltd.', 'UNUSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '10 years'
)
ON CONFLICT (code) DO NOTHING;
