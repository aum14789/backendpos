-- =============================================================
-- V28: Multi-Brand Expansion: Shabu (2 Branches), Japanese A la carte (1 Branch), Cafe (1 Branch)
-- =============================================================

-- 1. Brands
INSERT INTO brands (id, company_id, name, code, description, is_active) VALUES
('brand-001', 'comp-001', 'Sun Shabu & Grill (ซันชาบู แอนด์ กริลล์)', 'SUN-SHABU', 'บุฟเฟ่ต์ชาบูและปิ้งย่างพรีเมียม สั่งไม่อั้น 2 สาขาในสุขุมวิท', TRUE),
('brand-002', 'comp-001', 'Sun Japanese Dining & Izakaya (ซัน อาหารญี่ปุ่น อะลาคาร์ท)', 'SUN-IZAKAYA', 'อาหารญี่ปุ่นต้นตำรับ ซาชิมิ ซูชิโอโทโร่ ดงบุริ และราเมงแบบอะลาคาร์ท', TRUE),
('brand-003', 'comp-001', 'Sun Coffee & Artisan Bakery (ซัน คาเฟ่ & เบเกอรี่)', 'SUN-CAFE', 'สเปเชียลตี้คอฟฟี่ เดอร์ตี้ ครัวซองต์เนยฝรั่งเศส และชีสเค้กโฮมเมด', TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, code = EXCLUDED.code, description = EXCLUDED.description;

-- 2. Branches
-- Shabu Branch 1 (Sukhumvit 24) & Branch 2 (Sukhumvit Asoke)
INSERT INTO branches (id, company_id, brand_id, name, code, address, phone, business_day_close_time, tax_rate, service_charge_rate, is_active) VALUES
('branch-001', 'comp-001', 'brand-001', 'Sun Shabu Sukhumvit 24 (สุขุมวิท 24)', 'SUK-01', '888 ซอยสุขุมวิท 24 แขวงคลองตัน เขตคลองเตย กรุงเทพฯ 10110', '02-123-4561', '02:00', 7.00, 10.00, TRUE),
('branch-002', 'comp-001', 'brand-001', 'Sun Shabu Sukhumvit Asoke (สุขุมวิท อโศก)', 'SUK-02', '219 อาคารอโศกทาวเวอร์ ถนนสุขุมวิท 21 แขวงคลองเตยเหนือ เขตวัฒนา กรุงเทพฯ 10110', '02-123-4562', '02:00', 7.00, 10.00, TRUE),
('branch-003', 'comp-001', 'brand-002', 'Sun Japanese Thonglor (สาขาทองหล่อ)', 'JPN-01', '55/1 ซอยทองหล่อ 13 แขวงคลองตันเหนือ เขตวัฒนา กรุงเทพฯ 10110', '02-712-8899', '00:00', 7.00, 10.00, TRUE),
('branch-004', 'comp-001', 'brand-003', 'Sun Coffee Ari Craft Cafe (สาขาอารีย์)', 'CAF-01', '12/4 ซอยอารีย์สัมพันธ์ 5 แขวงพญาไท เขตพญาไท กรุงเทพฯ 10400', '02-619-7788', '21:00', 7.00, 0.00, TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, code = EXCLUDED.code, brand_id = EXCLUDED.brand_id, address = EXCLUDED.address;

-- 3. Warehouses for all branches
INSERT INTO warehouses (id, branch_id, name, code, is_central, is_active) VALUES
('wh-001', 'branch-001', 'คลังหลักสุขุมวิท 24 (Sukhumvit 24 WH)', 'WH-SUK-01', FALSE, TRUE),
('wh-002', 'branch-002', 'คลังหลักสุขุมวิท อโศก (Sukhumvit Asoke WH)', 'WH-SUK-02', FALSE, TRUE),
('wh-003', 'branch-003', 'คลังวัตถุดิบอาหารญี่ปุ่นทองหล่อ (Thonglor Kitchen WH)', 'WH-JPN-01', FALSE, TRUE),
('wh-004', 'branch-004', 'คลังวัตถุดิบและเบเกอรี่อารีย์ (Ari Cafe & Bakery WH)', 'WH-CAF-01', FALSE, TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, branch_id = EXCLUDED.branch_id;

-- 4. Menu Categories
-- Brand 2: Japanese A la carte Categories
INSERT INTO menu_categories (id, branch_id, brand_id, name, sort_order, is_active) VALUES
('cat-jp-01', 'branch-003', 'brand-002', 'ซาชิมิ & ซูชิพรีเมียม (Sashimi & Sushi)', 1, TRUE),
('cat-jp-02', 'branch-003', 'brand-002', 'ข้าวหน้าดงบุริ & วากิว (Donburi & Wagyu)', 2, TRUE),
('cat-jp-03', 'branch-003', 'brand-002', 'ราเมง & เมนูเส้น (Ramen & Noodles)', 3, TRUE),
('cat-jp-04', 'branch-003', 'brand-002', 'เมนูทานเล่น & อิซากายะ (Appetizers & Fried)', 4, TRUE),
('cat-jp-05', 'branch-003', 'brand-002', 'เครื่องดื่มญี่ปุ่น (Japanese Drinks)', 5, TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, branch_id = EXCLUDED.branch_id;

-- Brand 3: Cafe Categories
INSERT INTO menu_categories (id, branch_id, brand_id, name, sort_order, is_active) VALUES
('cat-cf-01', 'branch-004', 'brand-003', 'สเปเชียลตี้คอฟฟี่ (Specialty Coffee)', 1, TRUE),
('cat-cf-02', 'branch-004', 'brand-003', 'ชา & เครื่องดื่มเย็นสดชื่น (Tea & Refreshers)', 2, TRUE),
('cat-cf-03', 'branch-004', 'brand-003', 'ครัวซองต์ & เบเกอรี่อบสด (Artisan Bakery)', 3, TRUE),
('cat-cf-04', 'branch-004', 'brand-003', 'เค้กโฮมเมด & ของหวาน (Homemade Cakes)', 4, TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, branch_id = EXCLUDED.branch_id;

-- 5. Menu Items
-- Brand 2: Japanese A la carte Items
INSERT INTO menu_items (id, branch_id, brand_id, category_id, name, sku, base_price, availability, is_active) VALUES
('item-jp-01', 'branch-003', 'brand-002', 'cat-jp-01', 'ซาชิมิแซลมอนนอร์เวย์พรีเมียม (Salmon Sashimi)', 'SKU-JP-001', 350.00, 'AVAILABLE', TRUE),
('item-jp-02', 'branch-003', 'brand-002', 'cat-jp-02', 'ข้าวหน้าเนื้อวากิวภูเขาไฟไข่ดอง (Wagyu Lava Don)', 'SKU-JP-002', 450.00, 'AVAILABLE', TRUE),
('item-jp-03', 'branch-003', 'brand-002', 'cat-jp-03', 'ราเมงซุปกระดูกหมูทงคัตสึชาชู (Tonkotsu Ramen)', 'SKU-JP-003', 240.00, 'AVAILABLE', TRUE),
('item-jp-04', 'branch-003', 'brand-002', 'cat-jp-01', 'เซ็ตซูชิโอโทโร่ & ปลาไหลอุนางิ (Otoro & Unagi Set)', 'SKU-JP-004', 590.00, 'AVAILABLE', TRUE),
('item-jp-05', 'branch-003', 'brand-002', 'cat-jp-04', 'เทมปุระกุ้งลายเสือรวมมิตร (Ebi Tempura)', 'SKU-JP-005', 260.00, 'AVAILABLE', TRUE),
('item-jp-06', 'branch-003', 'brand-002', 'cat-jp-04', 'สลัดหนังปลาแซลมอนกรอบน้ำสลัดงา (Salmon Skin Salad)', 'SKU-JP-006', 180.00, 'AVAILABLE', TRUE),
('item-jp-07', 'branch-003', 'brand-002', 'cat-jp-04', 'ไก่ทอดคาราเกะซอสสไปซี่มาโย (Tori Karaage)', 'SKU-JP-007', 145.00, 'AVAILABLE', TRUE),
('item-jp-08', 'branch-003', 'brand-002', 'cat-jp-05', 'ชาเขียวมัทฉะอุจิเย็นพรีเมียม (Uji Iced Matcha)', 'SKU-JP-008', 85.00, 'AVAILABLE', TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, base_price = EXCLUDED.base_price, category_id = EXCLUDED.category_id;

-- Brand 3: Cafe & Bakery Items
INSERT INTO menu_items (id, branch_id, brand_id, category_id, name, sku, base_price, availability, is_active) VALUES
('item-cf-01', 'branch-004', 'brand-003', 'cat-cf-01', 'Signature Dirty Coffee (เดอร์ตี้ กาแฟนมสกัดเย็น)', 'SKU-CF-001', 120.00, 'AVAILABLE', TRUE),
('item-cf-02', 'branch-004', 'brand-003', 'cat-cf-01', 'Cold Brew Yuzu Orange Tonic (โคลด์บรูว์ ส้มยูซุ)', 'SKU-CF-002', 135.00, 'AVAILABLE', TRUE),
('item-cf-03', 'branch-004', 'brand-003', 'cat-cf-01', 'Artisan Hot Cafe Latte (ลาเต้ร้อน เมล็ดไทย-บราซิล)', 'SKU-CF-003', 95.00, 'AVAILABLE', TRUE),
('item-cf-04', 'branch-004', 'brand-003', 'cat-cf-01', 'Iced Salted Caramel Macchiato', 'SKU-CF-004', 125.00, 'AVAILABLE', TRUE),
('item-cf-05', 'branch-004', 'brand-003', 'cat-cf-03', 'ครัวซองต์เนยสดฝรั่งเศส (French Butter Croissant)', 'SKU-CF-005', 85.00, 'AVAILABLE', TRUE),
('item-cf-06', 'branch-004', 'brand-003', 'cat-cf-03', 'ครัวซองต์อัลมอนด์ครีมสด (Almond Croissant)', 'SKU-CF-006', 115.00, 'AVAILABLE', TRUE),
('item-cf-07', 'branch-004', 'brand-003', 'cat-cf-02', 'ชาไทยพรีเมียมเย็น Signature (Royal Iced Thai Tea)', 'SKU-CF-007', 90.00, 'AVAILABLE', TRUE),
('item-cf-08', 'branch-004', 'brand-003', 'cat-cf-04', 'บาสก์ชีสเค้กหน้าไหม้ (Basque Burnt Cheesecake)', 'SKU-CF-008', 150.00, 'AVAILABLE', TRUE)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, base_price = EXCLUDED.base_price, category_id = EXCLUDED.category_id;
