-- V13 dropped leave_types_code_key / departments_code_key from the original Flyway schema.
-- Hibernate ddl-auto=update may also have created single-column UNIQUE constraints with
-- auto-generated names (e.g. ukc7knnvg6a1wkmt3f2gciae83e on leave_types.code). Those block
-- per-tenant seeding when a second org tries to insert ANNUAL. Drop any remaining
-- single-column uniques on code/name; keep composite per-org uniques from V13.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT t.relname AS table_name, c.conname
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON n.oid = t.relnamespace AND n.nspname = 'public'
        WHERE c.contype = 'u'
          AND t.relname IN ('leave_types', 'departments')
          AND array_length(c.conkey, 1) = 1
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I', r.table_name, r.conname);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_departments_org_code
    ON departments (organization_id, code);

CREATE UNIQUE INDEX IF NOT EXISTS uq_leave_types_org_code
    ON leave_types (organization_id, code);

CREATE UNIQUE INDEX IF NOT EXISTS uq_leave_types_org_name
    ON leave_types (organization_id, name);
