-- V5__user_username_index.sql
-- ADR 0033: login looks users up by username on every attempt (findByUsernameAndIsActiveTrue).
-- The baseline only indexed (company_id) / (company_id, is_active); add a username index
-- so the login lookup never falls back to a sequential scan as the users table grows.

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
