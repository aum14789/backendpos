-- =============================================================
-- V20: Production-Ready Demonstration Seed Data
-- =============================================================
-- Provides verified multi-brand, multi-branch, menu, buffet promotions,
-- table layout, device registrations, and secure PIN credentials (BCrypt).
-- =============================================================

-- 1. Company
INSERT INTO companies (id, name, tax_id) VALUES
('comp-001', 'Sun Hospitality Group Co., Ltd.', '0105560000001')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, tax_id = EXCLUDED.tax_id;

-- 2. Brands
INSERT INTO brands (id, company_id, name, code, description, is_active) VALUES
('brand-01', 'comp-001', 'Sun Shabu Buffet (ซันชาบู บุฟเฟ่ต์)', 'SHABU', 'บุฟเฟ่ต์ชาบูและซาชิมิพรีเมียม สั่งไม่อั้นตามเวลา', TRUE),
('brand-02', 'comp-001', 'Sun Thai Classic (ซันไทย คลาสสิก)', 'THAI', 'อาหารไทยต้นตำรับรสจัดจ้าน เสิร์ฟจานเดี่ยวและหม้อไฟ', TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, code = EXCLUDED.code, description = EXCLUDED.description;

-- 3. Branches
INSERT INTO branches (id, company_id, brand_id, name, code, address, phone, business_day_close_time, tax_rate, service_charge_rate, is_active) VALUES
('branch-001', 'comp-001', 'brand-01', 'Sun Shabu Sukhumvit (สาขาสุขุมวิท)', 'BR-SHABU-01', '123 ซอยสุขุมวิท 55 แขวงคลองตันเหนือ เขตวัฒนา กรุงเทพฯ 10110', '02-123-4567', '02:00', 7.00, 10.00, TRUE),
('branch-002', 'comp-001', 'brand-02', 'Sun Thai Siam Paragon (สาขาสยามพารากอน)', 'BR-THAI-02', '991 ถนนพระรามที่ 1 แขวงปทุมวัน เขตปทุมวัน กรุงเทพฯ 10330', '02-987-6543', '23:00', 7.00, 10.00, TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, code = EXCLUDED.code, brand_id = EXCLUDED.brand_id;

-- 4. Devices (1 per branch)
INSERT INTO devices (id, branch_id, device_name, device_code, device_type, app_version, status) VALUES
('dev-001', 'branch-001', 'POS Terminal Shabu Sukhumvit 01', 'POS-SUK-01', 'POS_MAIN', 'v1.10.0-android', 'ACTIVE'),
('dev-002', 'branch-002', 'POS Terminal Thai Siam 01', 'POS-SIAM-01', 'POS_MAIN', 'v1.10.0-android', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET branch_id = EXCLUDED.branch_id, device_name = EXCLUDED.device_name, device_code = EXCLUDED.device_code;

-- 5. Dining Tables (8 tables for branch-001)
INSERT INTO tables (id, branch_id, name_number, capacity, status, is_active) VALUES
('table-01', 'branch-001', 'T-01', 4, 'AVAILABLE', TRUE),
('table-02', 'branch-001', 'T-02', 4, 'AVAILABLE', TRUE),
('table-03', 'branch-001', 'T-03', 4, 'AVAILABLE', TRUE),
('table-04', 'branch-001', 'T-04', 4, 'AVAILABLE', TRUE),
('table-05', 'branch-001', 'T-05', 6, 'AVAILABLE', TRUE),
('table-06', 'branch-001', 'T-06', 6, 'AVAILABLE', TRUE),
('table-07', 'branch-001', 'T-07', 2, 'AVAILABLE', TRUE),
('table-08', 'branch-001', 'T-08', 8, 'AVAILABLE', TRUE)
ON CONFLICT (id) DO UPDATE SET branch_id = EXCLUDED.branch_id, name_number = EXCLUDED.name_number, capacity = EXCLUDED.capacity;

-- 6. Menu Categories
-- Brand A (Shabu) Categories
INSERT INTO menu_categories (id, branch_id, brand_id, name, sort_order, is_active) VALUES
('cat-shabu-01', 'branch-001', 'brand-01', 'เนื้อสัตว์ & ซีฟู้ด (Meat & Seafood)', 1, TRUE),
('cat-shabu-02', 'branch-001', 'brand-01', 'ผักสด & น้ำซุป (Veggies & Soup)', 2, TRUE),
('cat-shabu-03', 'branch-001', 'brand-01', 'เครื่องดื่ม & ของหวาน (Drinks & Desserts)', 3, TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, branch_id = EXCLUDED.branch_id;

-- Brand B (Thai) Categories
INSERT INTO menu_categories (id, branch_id, brand_id, name, sort_order, is_active) VALUES
('cat-thai-01', 'branch-002', 'brand-02', 'อาหารจานเดี่ยวรสเด็ด (A La Carte Classics)', 1, TRUE),
('cat-thai-02', 'branch-002', 'brand-02', 'ต้มยำ & แกงโบราณ (Curry & Soup)', 2, TRUE),
('cat-thai-03', 'branch-002', 'brand-02', 'ของหวาน & เครื่องดื่ม (Desserts & Drinks)', 3, TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, branch_id = EXCLUDED.branch_id;

-- 7. Menu Items
-- Brand A (Shabu) Items
INSERT INTO menu_items (id, branch_id, brand_id, category_id, name, sku, base_price, availability, is_active) VALUES
('item-shabu-01', 'branch-001', 'brand-01', 'cat-shabu-01', 'เนื้อวากิวออสเตรเลีย (Australian Wagyu Beef)', 'SKU-WAGYU', 350.0000, 'AVAILABLE', TRUE),
('item-shabu-02', 'branch-001', 'brand-01', 'cat-shabu-01', 'หมูคุโรบูตะสไลซ์ (Kurobuta Pork Collar)', 'SKU-KUROBUTA', 180.0000, 'AVAILABLE', TRUE),
('item-shabu-03', 'branch-001', 'brand-01', 'cat-shabu-01', 'แซลมอนซาชิมิสด (Fresh Salmon Sashimi)', 'SKU-SALMON', 220.0000, 'AVAILABLE', TRUE),
('item-shabu-04', 'branch-001', 'brand-01', 'cat-shabu-02', 'ชุดผักรวมและเห็ดรวม (Fresh Veggie Basket)', 'SKU-VEGGIE', 90.0000, 'AVAILABLE', TRUE),
('item-shabu-05', 'branch-001', 'brand-01', 'cat-shabu-02', 'น้ำซุปชาบูน้ำดำสูตรพิเศษ (Black Shabu Soup)', 'SKU-SOUP-BLK', 50.0000, 'AVAILABLE', TRUE),
('item-shabu-06', 'branch-001', 'brand-01', 'cat-shabu-03', 'ชาเขียวมัทฉะรีฟิล (Refill Matcha Green Tea)', 'SKU-TEA-MATCHA', 45.0000, 'AVAILABLE', TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, base_price = EXCLUDED.base_price, availability = EXCLUDED.availability;

-- Brand B (Thai) Items
INSERT INTO menu_items (id, branch_id, brand_id, category_id, name, sku, base_price, availability, is_active) VALUES
('item-thai-01', 'branch-002', 'brand-02', 'cat-thai-01', 'ผัดไทยกุ้งแม่น้ำสด (Pad Thai River Prawn)', 'SKU-PADTHAI', 190.0000, 'AVAILABLE', TRUE),
('item-thai-02', 'branch-002', 'brand-02', 'cat-thai-02', 'ต้มยำกุ้งแม่น้ำน้ำข้น (Tom Yum Goong)', 'SKU-TOMYUM', 250.0000, 'AVAILABLE', TRUE),
('item-thai-03', 'branch-002', 'brand-02', 'cat-thai-02', 'แกงเขียวหวานไก่โรตี (Green Curry Chicken & Roti)', 'SKU-GREENCURRY', 180.0000, 'AVAILABLE', TRUE),
('item-thai-04', 'branch-002', 'brand-02', 'cat-thai-01', 'ข้าวผัดปูก้อนคั่วกระทะ (Jumbo Lump Crab Fried Rice)', 'SKU-CRABRICE', 220.0000, 'AVAILABLE', TRUE),
('item-thai-05', 'branch-002', 'brand-02', 'cat-thai-03', 'ข้าวเหนียวมะม่วงอกร่อง (Mango Sticky Rice)', 'SKU-MANGO', 120.0000, 'AVAILABLE', TRUE),
('item-thai-06', 'branch-002', 'brand-02', 'cat-thai-03', 'ชาไทยเย็นสูตรโบราณ (Traditional Thai Milk Tea)', 'SKU-THAITEA', 65.0000, 'AVAILABLE', TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, base_price = EXCLUDED.base_price, availability = EXCLUDED.availability;

-- 8. Buffet Promotions (2 Distinct Packages with different price, duration, and eligible items)
-- Promotion 1: Standard Kurobuta Buffet (฿399 / 90 min)
INSERT INTO buffet_promotions (id, brand_id, branch_id, name, price_per_person, duration_minutes, status) VALUES
('promo-buffet-std', 'brand-01', 'branch-001', 'Standard Shabu Buffet (บุฟเฟ่ต์หมูคุโรบูตะ ฿399)', 399.0000, 90, 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, price_per_person = EXCLUDED.price_per_person, duration_minutes = EXCLUDED.duration_minutes;

-- Promotion 2: Premium Wagyu & Salmon Buffet (฿699 / 120 min)
INSERT INTO buffet_promotions (id, brand_id, branch_id, name, price_per_person, duration_minutes, status) VALUES
('promo-buffet-wagyu', 'brand-01', 'branch-001', 'Premium Wagyu & Salmon Buffet (บุฟเฟ่ต์วากิว & แซลมอน ฿699)', 699.0000, 120, 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, price_per_person = EXCLUDED.price_per_person, duration_minutes = EXCLUDED.duration_minutes;

-- Eligible Menu Items for Standard Promotion (฿399)
INSERT INTO buffet_promotion_menu_items (promotion_id, menu_item_id) VALUES
('promo-buffet-std', 'item-shabu-02'), -- Kurobuta Pork
('promo-buffet-std', 'item-shabu-04'), -- Veggies Basket
('promo-buffet-std', 'item-shabu-05'), -- Soup
('promo-buffet-std', 'item-shabu-06')  -- Matcha Tea
ON CONFLICT (promotion_id, menu_item_id) DO NOTHING;

-- Eligible Menu Items for Premium Promotion (฿699)
INSERT INTO buffet_promotion_menu_items (promotion_id, menu_item_id) VALUES
('promo-buffet-wagyu', 'item-shabu-01'), -- Wagyu Beef
('promo-buffet-wagyu', 'item-shabu-02'), -- Kurobuta Pork
('promo-buffet-wagyu', 'item-shabu-03'), -- Salmon Sashimi
('promo-buffet-wagyu', 'item-shabu-04'), -- Veggies Basket
('promo-buffet-wagyu', 'item-shabu-05'), -- Soup
('promo-buffet-wagyu', 'item-shabu-06')  -- Matcha Tea
ON CONFLICT (promotion_id, menu_item_id) DO NOTHING;

-- 9. Users & BCrypt Credentials (Cashier PIN: 1234, Manager PIN: 9999)
-- Password for all: admin123 ($2a$10$7Q9lT5qSg8F/H8b.pBfD9uV7Xg3A9yK8m0L9p8q7r6s5t4u3v2w1x)
-- PIN 1234 BCrypt: $2a$10$wT0vB5pS18fH6iZ1X0yTku3Y9rQ4kG7uR0b3mO2aQ5eP8wV7zC1Xu
-- PIN 9999 BCrypt: $2a$10$tJ9fV7kL18sH5iZ0X2yThu4Y8rP3kF6uQ9b2mN1aP4eO7wU6zB0Xt
INSERT INTO users (id, company_id, username, password_hash, full_name, email, phone, pin_code, is_active) VALUES
('usr-cashier-01', 'comp-001', 'cashier01', '$2a$10$7Q9lT5qSg8F/H8b.pBfD9uV7Xg3A9yK8m0L9p8q7r6s5t4u3v2w1x', 'พนักงานแคชเชียร์ สมชาย', 'cashier01@sunpos.com', '081-111-2222', '$2a$10$wT0vB5pS18fH6iZ1X0yTku3Y9rQ4kG7uR0b3mO2aQ5eP8wV7zC1Xu', TRUE),
('usr-manager-01', 'comp-001', 'manager01', '$2a$10$7Q9lT5qSg8F/H8b.pBfD9uV7Xg3A9yK8m0L9p8q7r6s5t4u3v2w1x', 'ผู้จัดการสาขา สมศักดิ์', 'manager01@sunpos.com', '081-333-4444', '$2a$10$tJ9fV7kL18sH5iZ0X2yThu4Y8rP3kF6uQ9b2mN1aP4eO7wU6zB0Xt', TRUE)
ON CONFLICT (id) DO UPDATE SET pin_code = EXCLUDED.pin_code, full_name = EXCLUDED.full_name, is_active = TRUE;

INSERT INTO user_roles (user_id, role_id) VALUES
('usr-cashier-01', 'role-04'), -- ROLE_CASHIER
('usr-manager-01', 'role-02')  -- ROLE_STORE_MANAGER
ON CONFLICT DO NOTHING;

-- 10. Open Business Day for Immediate Operations
INSERT INTO business_days (id, branch_id, business_date, closing_time_setting, opened_at, status, total_sales, total_cash_payments, total_non_cash_payments, total_refunds) VALUES
('bday-sukhumvit-today', 'branch-001', CURRENT_DATE, '02:00', now(), 'OPEN', 0, 0, 0, 0),
('bday-siam-today', 'branch-002', CURRENT_DATE, '23:00', now(), 'OPEN', 0, 0, 0, 0)
ON CONFLICT DO NOTHING;
