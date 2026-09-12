-- ADR 0012: Populate activation codes for baseline branches and activation_codes table

-- 1. Ensure existing baseline branches have their canonical activation codes in branches table
UPDATE branches
SET activation_code = 'SUN-HQ01-POS01'
WHERE id = 'branch-001' AND (activation_code IS NULL OR activation_code = '');

UPDATE branches
SET activation_code = 'SUN-LP02-POS01'
WHERE id = 'branch-002' AND (activation_code IS NULL OR activation_code = '');

-- 2. Populate activation_codes table with valid initial tokens
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
