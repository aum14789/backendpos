-- TEST-ONLY migration. Never applied to a real environment: it lives in
-- `src/test/resources`, and only the `test` profile adds `classpath:db/test-migration`
-- to Flyway's locations (see application-test.yml).
--
-- Why: the V1 baseline defines 146 immediate FOREIGN KEY constraints. The test
-- corpus predates it -- it was written against Firestore, which has no referential
-- integrity at all -- so it fabricates parent ids (`branchId = "branch-001"`) that
-- no `branches` row ever satisfies. Under immediate constraints every one of those
-- writes is rejected and ~19 classes fail before asserting anything.
--
-- Making the constraints DEFERRABLE INITIALLY DEFERRED moves the check from INSERT
-- time to COMMIT time. Tests are `@Transactional` and roll back, so they never reach
-- a COMMIT and never trip the check, while production keeps every constraint and
-- still validates referential integrity on commit.
--
-- The ALTER is driven from the catalog rather than written out 146 times, so this
-- cannot drift when the baseline changes.
DO $$
DECLARE
    fk RECORD;
    altered INT := 0;
BEGIN
    FOR fk IN
        SELECT c.conrelid::regclass AS table_name, c.conname AS constraint_name
        FROM pg_constraint c
        JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE c.contype = 'f'
          AND n.nspname = 'public'
          AND NOT c.condeferrable
    LOOP
        EXECUTE format(
            'ALTER TABLE %s ALTER CONSTRAINT %I DEFERRABLE INITIALLY DEFERRED',
            fk.table_name,
            fk.constraint_name
        );
        altered := altered + 1;
    END LOOP;

    RAISE NOTICE 'Deferred % foreign key constraints for the test profile', altered;
END $$;
