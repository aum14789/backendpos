-- ADR 0005 extension: ผูก Printer กับ Kitchen Station โดยตรง (branch-scoped)
-- แทนที่การผูกผ่าน menu_categories สำหรับ kitchen printing
ALTER TABLE printers ADD COLUMN IF NOT EXISTS kitchen_station_id VARCHAR(255);
