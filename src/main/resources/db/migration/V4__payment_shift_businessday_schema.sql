-- SunPOS V4__payment_shift_businessday_schema.sql
-- Payment, Refund, Cashier Shift, Cash Movement, and Business Day schema

-- 1. Payment Transactions
CREATE TABLE IF NOT EXISTS payment_transactions (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id),
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    device_id VARCHAR(36),
    shift_id VARCHAR(36),
    payment_method VARCHAR(50) NOT NULL, -- CASH, CARD, QR, PROMPTPAY, EWALLET, VOUCHER, OTHER
    amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    tendered_amount NUMERIC(15, 4) DEFAULT 0.0000,
    change_amount NUMERIC(15, 4) DEFAULT 0.0000,
    status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS', -- PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED, PARTIALLY_REFUNDED
    idempotency_key VARCHAR(100) UNIQUE,
    external_ref VARCHAR(100),
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Refund Transactions
CREATE TABLE IF NOT EXISTS refund_transactions (
    id VARCHAR(36) PRIMARY KEY,
    payment_transaction_id VARCHAR(36) NOT NULL REFERENCES payment_transactions(id),
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id),
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    reason TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED', -- COMPLETED, FAILED
    approved_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Cashier Shifts
CREATE TABLE IF NOT EXISTS cashier_shifts (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    device_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN', -- OPEN, CLOSED
    opening_cash NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    cash_sales NUMERIC(15, 4) DEFAULT 0.0000,
    cash_in NUMERIC(15, 4) DEFAULT 0.0000,
    cash_out NUMERIC(15, 4) DEFAULT 0.0000,
    refund_cash NUMERIC(15, 4) DEFAULT 0.0000,
    expected_cash NUMERIC(15, 4) DEFAULT 0.0000,
    actual_cash NUMERIC(15, 4) DEFAULT 0.0000,
    variance NUMERIC(15, 4) DEFAULT 0.0000,
    variance_type VARCHAR(50) DEFAULT 'ZERO', -- ZERO, OVER, SHORT
    closing_notes TEXT
);

-- 4. Cash Movements (Cash In / Cash Out)
CREATE TABLE IF NOT EXISTS cash_movements (
    id VARCHAR(36) PRIMARY KEY,
    shift_id VARCHAR(36) NOT NULL REFERENCES cashier_shifts(id),
    movement_type VARCHAR(50) NOT NULL, -- CASH_IN, CASH_OUT
    amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    reason TEXT,
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Business Days
CREATE TABLE IF NOT EXISTS business_days (
    id VARCHAR(36) PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL REFERENCES branches(id),
    business_date DATE NOT NULL,
    closing_time_setting VARCHAR(10) DEFAULT '02:00',
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN', -- OPEN, PROCESSING, CLOSED
    total_sales NUMERIC(15, 4) DEFAULT 0.0000,
    total_cash_payments NUMERIC(15, 4) DEFAULT 0.0000,
    total_non_cash_payments NUMERIC(15, 4) DEFAULT 0.0000,
    total_refunds NUMERIC(15, 4) DEFAULT 0.0000,
    closed_by VARCHAR(36),
    CONSTRAINT uk_branch_business_date UNIQUE (branch_id, business_date)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_payments_order ON payment_transactions(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_branch ON payment_transactions(branch_id);
CREATE INDEX IF NOT EXISTS idx_refunds_payment ON refund_transactions(payment_transaction_id);
CREATE INDEX IF NOT EXISTS idx_shifts_branch_device ON cashier_shifts(branch_id, device_id);
CREATE INDEX IF NOT EXISTS idx_business_days_branch ON business_days(branch_id);

-- Seed Initial Open Business Day
INSERT INTO business_days (id, branch_id, business_date, closing_time_setting, status) VALUES
('bday-001', 'branch-001', CURRENT_DATE, '02:00', 'OPEN')
ON CONFLICT (id) DO NOTHING;

-- Seed Initial Open Cashier Shift
INSERT INTO cashier_shifts (id, branch_id, device_id, user_id, status, opening_cash) VALUES
('shift-001', 'branch-001', 'dev-001', 'usr-001', 'OPEN', 1000.0000)
ON CONFLICT (id) DO NOTHING;
