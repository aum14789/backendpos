-- ADR 0005 extension v2: Printer รับพิมพ์ได้หลาย Kitchen Station (many-to-many)
-- เพิ่มตาราง printer_kitchen_stations แทนการใช้คอลัมน์เดียว kitchen_station_id
-- คอลัมน์เดิมยังคงไว้เพื่อ backward compat กับ POS sync รุ่นเก่า (deprecated)

CREATE TABLE IF NOT EXISTS printer_kitchen_stations (
    printer_id  VARCHAR(255) NOT NULL,
    station_id  VARCHAR(255) NOT NULL,
    PRIMARY KEY (printer_id, station_id)
);

-- migrate ข้อมูลเดิมจาก kitchen_station_id column เข้าตารางใหม่
INSERT INTO printer_kitchen_stations (printer_id, station_id)
SELECT id, kitchen_station_id
FROM printers
WHERE kitchen_station_id IS NOT NULL AND kitchen_station_id != ''
ON CONFLICT DO NOTHING;
