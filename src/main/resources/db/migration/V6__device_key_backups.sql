-- ADR 0034 / spec 0037 (ticket 05): Cloud key backup
-- สำเนากุญแจเข้ารหัสฐานข้อมูลออฟไลน์ของแต่ละเครื่อง POS (ต่อ device)
-- ค่า wrapped_key ถูก wrap ด้วย master key ฝั่ง server ก่อนเก็บเสมอ
-- (cloud ไม่เห็นกุญแจเปล่า) — ดึง/เก็บบังคับสิทธิ์ HQ ที่ controller

CREATE TABLE IF NOT EXISTS device_key_backups (
    device_id VARCHAR(64) NOT NULL,
    wrapped_key TEXT NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_device_key_backups PRIMARY KEY (device_id)
);
