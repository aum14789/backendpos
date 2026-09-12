-- V4__add_assigned_modules_to_users_and_seed_manager_pin.sql
-- 1. Add assigned_modules column to users table for ERP sidebar module permissions (ADR 0014)
ALTER TABLE users ADD COLUMN IF NOT EXISTS assigned_modules TEXT;

-- 2. Populate assigned_modules for default seeded users
UPDATE users 
SET assigned_modules = 'REPORTS,STORE_OPERATIONS,MENU_PROMOTIONS,INVENTORY_PURCHASING,KITCHEN_PRODUCTION,CRM_LOYALTY,ORG_SETTINGS' 
WHERE username IN ('admin', 'manager01') AND (assigned_modules IS NULL OR assigned_modules = '');

UPDATE users 
SET assigned_modules = 'STORE_OPERATIONS' 
WHERE username = 'cashier01' AND (assigned_modules IS NULL OR assigned_modules = '');

-- 3. Update manager01 default PIN code to 9999 to align with POS login hint (BCrypt hash)
-- Cashier PIN remains 1234 ($2a$10$3n3nWX3a7salqcVriL.2.eVjGCbysLBhi0ReTThl26wy8IY8X5JCO)
UPDATE users 
SET pin_code = '$2a$10$H6N2ozIESJ4zMS23CpQuoO9Dfh6UH68ehZiak7fCaVomeX9jCqTru' 
WHERE username = 'manager01';
