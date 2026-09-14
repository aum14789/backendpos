-- V3: Add prefix column to menu_categories for category-driven item code generation
ALTER TABLE menu_categories ADD COLUMN IF NOT EXISTS prefix VARCHAR(20);
