-- Replace global UNIQUE on reference-data codes with per-organization composites so
-- multiple workspaces can each have ANNUAL / HR / etc. without 500s on insert.

ALTER TABLE departments DROP CONSTRAINT IF EXISTS departments_code_key;
ALTER TABLE leave_types DROP CONSTRAINT IF EXISTS leave_types_code_key;
ALTER TABLE leave_types DROP CONSTRAINT IF EXISTS leave_types_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_departments_org_code
    ON departments (organization_id, code);

CREATE UNIQUE INDEX IF NOT EXISTS uq_leave_types_org_code
    ON leave_types (organization_id, code);

CREATE UNIQUE INDEX IF NOT EXISTS uq_leave_types_org_name
    ON leave_types (organization_id, name);
