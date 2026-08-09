-- Novora HRMS — multi-tenancy + 30-day trial subscriptions (Phase 1).
-- Idempotent. Safe to re-run on Neon.
--
-- This migration runs via Flyway on startup (baseline V10 for existing Neon DBs).
-- Hibernate ddl-auto=update handles any remaining entity drift after V11/V12.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1) Organizations (workspaces / tenants)
CREATE TABLE IF NOT EXISTS organizations (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                VARCHAR(120) NOT NULL,
    slug                VARCHAR(80)  NOT NULL UNIQUE,
    plan                VARCHAR(20)  NOT NULL DEFAULT 'TRIAL'
                          CHECK (plan IN ('TRIAL', 'PAID', 'ENTERPRISE', 'EXPIRED')),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE', 'READ_ONLY', 'SUSPENDED')),
    trial_started_at    TIMESTAMP,
    trial_expires_at    TIMESTAMP,
    paid_until          TIMESTAMP,
    seats_purchased     INTEGER,
    stripe_customer_id  TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_organizations_slug    ON organizations(slug);
CREATE INDEX IF NOT EXISTS idx_organizations_plan    ON organizations(plan);
CREATE INDEX IF NOT EXISTS idx_organizations_status  ON organizations(status);
CREATE INDEX IF NOT EXISTS idx_organizations_expiry  ON organizations(trial_expires_at)
    WHERE plan = 'TRIAL' AND status = 'ACTIVE';

-- 2) Seed the implicit "Novora Internal" workspace for existing data.
--    plan=ENTERPRISE so it is never expired by the sweeper, status=ACTIVE so writes are allowed.
INSERT INTO organizations (id, name, slug, plan, status, created_at, updated_at)
SELECT uuid_generate_v4(), 'Novora Internal', 'novora-internal', 'ENTERPRISE', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM organizations WHERE slug = 'novora-internal');

-- 3) Add organization_id (NULLABLE for backfill) to every top-level tenant-scoped table.
--    Child tables (leave_requests, attendance, hr_payroll, etc.) inherit org via their parent.
ALTER TABLE users          ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE departments    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE positions      ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE employees      ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE leave_types    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE holidays       ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE training       ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE assets         ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE documents      ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE job_postings   ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE candidates     ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE announcements  ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;
ALTER TABLE audit_logs     ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;

-- 4) Backfill existing rows to the Internal workspace.
--    Run inside a single statement per table so it's restartable on partial failure.
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE users          SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE departments    SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE positions      SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE employees      SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE leave_types    SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE holidays       SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE training       SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE assets         SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE documents      SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE job_postings   SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE candidates     SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE announcements  SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;
WITH internal_org AS (SELECT id FROM organizations WHERE slug = 'novora-internal' LIMIT 1)
UPDATE audit_logs     SET organization_id = (SELECT id FROM internal_org) WHERE organization_id IS NULL;

-- 5) Per-tenant indexes that we expect every read query to hit.
CREATE INDEX IF NOT EXISTS idx_users_org          ON users(organization_id);
CREATE INDEX IF NOT EXISTS idx_departments_org    ON departments(organization_id);
CREATE INDEX IF NOT EXISTS idx_positions_org      ON positions(organization_id);
CREATE INDEX IF NOT EXISTS idx_employees_org      ON employees(organization_id);
CREATE INDEX IF NOT EXISTS idx_leave_types_org    ON leave_types(organization_id);
CREATE INDEX IF NOT EXISTS idx_holidays_org       ON holidays(organization_id);
CREATE INDEX IF NOT EXISTS idx_training_org       ON training(organization_id);
CREATE INDEX IF NOT EXISTS idx_assets_org         ON assets(organization_id);
CREATE INDEX IF NOT EXISTS idx_documents_org      ON documents(organization_id);
CREATE INDEX IF NOT EXISTS idx_job_postings_org   ON job_postings(organization_id);
CREATE INDEX IF NOT EXISTS idx_candidates_org     ON candidates(organization_id);
CREATE INDEX IF NOT EXISTS idx_announcements_org  ON announcements(organization_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_org     ON audit_logs(organization_id);

-- 6) Drop the old global UNIQUE constraint on positions(title)?
--    Keep it for now: titles are scoped per (organization_id, department_id) at the app layer.
--    A future migration can drop the global UNIQUE once we're confident.

-- 7) Drop the old global UNIQUE on holidays(holiday_date)? Same reasoning — keep, enforce at app layer.

-- 8) Notes for follow-up migration (V12+):
--    * Add NOT NULL constraint on every organization_id column once Hibernate has been bouncing for
--      a release with the new code path. Until then, leave nullable so a half-deployed instance can
--      still serve traffic against the old schema.
--    * Replace the global UNIQUE constraints listed above with composite ones:
--        UNIQUE (organization_id, code)         -- departments, leave_types
--        UNIQUE (organization_id, asset_code)   -- assets
--        UNIQUE (organization_id, employee_code) -- employees
--        UNIQUE (organization_id, email)        -- employees
--      Today, two orgs creating the same code would collide. The app generates
--      org-prefixed codes for new signups (E.g. "GEN-<org-id-prefix>") to avoid this.
