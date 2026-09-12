-- V5__seed_default_table_types.sql
-- Seed default table type 'type-std' into table_types so table references always satisfy foreign key constraint
INSERT INTO table_types (id, branch_id, name, code, is_default, created_at)
VALUES ('type-std', NULL, 'โต๊ะมาตรฐาน (Standard Table)', 'STD', true, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
