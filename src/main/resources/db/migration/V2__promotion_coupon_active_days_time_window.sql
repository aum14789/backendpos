-- ADR 0009: Active Days & Time Window for promotions and coupons
-- active_days: CSV of ISO day codes (MON..SUN), NULL/empty = every day
-- active_start_time / active_end_time: local time window "HH:MM",
--   end <= start means the window wraps past midnight; NULL = all day (legacy rows)

ALTER TABLE promotions
    ADD COLUMN IF NOT EXISTS active_days VARCHAR(56),
    ADD COLUMN IF NOT EXISTS active_start_time VARCHAR(5),
    ADD COLUMN IF NOT EXISTS active_end_time VARCHAR(5);

ALTER TABLE coupons
    ADD COLUMN IF NOT EXISTS active_days VARCHAR(56),
    ADD COLUMN IF NOT EXISTS active_start_time VARCHAR(5),
    ADD COLUMN IF NOT EXISTS active_end_time VARCHAR(5);

-- COUPON_OVERRIDE permission (ADR 0009) — ปิดบิล cash แทนคูปองที่หลุดวัน/เวลา
INSERT INTO permissions (id, code, description)
VALUES ('perm-14', 'COUPON_OVERRIDE', 'Close bill with cash discount in place of a coupon outside its active days/time window (ADR 0009)')
ON CONFLICT (id) DO NOTHING;
