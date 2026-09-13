-- V2: Create kitchen_stations table and enhance menu_categories for brand-wide usage

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

CREATE INDEX IF NOT EXISTS idx_kitchen_stations_branch ON kitchen_stations(branch_id);
CREATE INDEX IF NOT EXISTS idx_kitchen_stations_brand ON kitchen_stations(brand_id);

-- Make branch_id optional in menu_categories to support brand-level / master catalog categories
ALTER TABLE menu_categories ALTER COLUMN branch_id DROP NOT NULL;

-- Seed initial 5 standard kitchen stations
INSERT INTO kitchen_stations (id, code, name, description, sort_order, is_active) VALUES
('ks-001', 'MAIN_KITCHEN', 'ครัวหลัก (Main Kitchen)', 'จุดเตรียมและปรุงอาหารจานหลักทั่วไป', 1, true),
('ks-002', 'HOT_KITCHEN', 'ครัวร้อน (Hot Kitchen)', 'เตาผัด ต้ม ทอด สเต๊ก อาหารปรุงสุกด้วยความร้อน', 2, true),
('ks-003', 'COLD_KITCHEN', 'ครัวเย็น (Cold Kitchen / Salad)', 'สลัด ยำ ของสด ซาชิมิ ผลไม้', 3, true),
('ks-004', 'BAR', 'บาร์น้ำ / เครื่องดื่ม (Beverage Bar)', 'เครื่องดื่ม ชา กาแฟ ค็อกเทล น้ำปั่น', 4, true),
('ks-005', 'DESSERT', 'สเตชันของหวาน (Dessert)', 'เบเกอรี่ ขนมหวาน ไอศกรีม', 5, true)
ON CONFLICT (id) DO NOTHING;
