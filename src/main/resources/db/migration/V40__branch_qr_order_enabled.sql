-- Branch-level kill switch for public QR Order web (temporary outage / network issues)
ALTER TABLE branches
    ADD COLUMN IF NOT EXISTS is_qr_order_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN branches.is_qr_order_enabled IS 'When false, public QR Order web is temporarily unavailable for this branch';
