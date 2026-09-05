-- Add id column with UUID default to role_permissions and user_roles to support standard JdbcRepository operations
ALTER TABLE role_permissions ADD COLUMN IF NOT EXISTS id VARCHAR(255) DEFAULT gen_random_uuid();
ALTER TABLE user_roles ADD COLUMN IF NOT EXISTS id VARCHAR(255) DEFAULT gen_random_uuid();

UPDATE role_permissions SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE user_roles SET id = gen_random_uuid() WHERE id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_role_permissions_id') THEN
        ALTER TABLE role_permissions ADD CONSTRAINT uq_role_permissions_id UNIQUE (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_user_roles_id') THEN
        ALTER TABLE user_roles ADD CONSTRAINT uq_user_roles_id UNIQUE (id);
    END IF;
END $$;
