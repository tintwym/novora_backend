-- Product completeness: offers, roster, allowances, branches, family/education, org profile.

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS legal_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS registration_no VARCHAR(80),
    ADD COLUMN IF NOT EXISTS address_line1 TEXT,
    ADD COLUMN IF NOT EXISTS city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS country VARCHAR(100),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(40),
    ADD COLUMN IF NOT EXISTS website VARCHAR(255);

ALTER TABLE interviews
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE RESTRICT;

UPDATE interviews i
SET organization_id = c.organization_id
FROM candidates c
WHERE i.candidate_id = c.id
  AND i.organization_id IS NULL
  AND c.organization_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_interviews_org ON interviews(organization_id);

CREATE TABLE IF NOT EXISTS job_offers (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    candidate_id    UUID NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    salary          DECIMAL(15,2),
    currency        VARCHAR(3) NOT NULL DEFAULT 'SGD',
    allowance       DECIMAL(15,2),
    grade           VARCHAR(50),
    probation       VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'draft'
                    CHECK (status IN ('sent', 'accepted', 'declined', 'draft')),
    sent_at         TIMESTAMP,
    expiry_date     DATE,
    notes           TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_job_offers_org ON job_offers(organization_id);
CREATE INDEX IF NOT EXISTS idx_job_offers_candidate ON job_offers(candidate_id);

CREATE TABLE IF NOT EXISTS shift_patterns (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name            VARCHAR(100) NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    break_mins      INTEGER NOT NULL DEFAULT 60,
    color           VARCHAR(20),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shift_patterns_org ON shift_patterns(organization_id);

CREATE TABLE IF NOT EXISTS roster_entries (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    employee_id      UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    work_date        DATE NOT NULL,
    shift_pattern_id UUID REFERENCES shift_patterns(id) ON DELETE SET NULL,
    status           VARCHAR(30) NOT NULL DEFAULT 'scheduled',
    notes            TEXT,
    created_at       TIMESTAMP DEFAULT NOW(),
    UNIQUE (employee_id, work_date)
);

CREATE INDEX IF NOT EXISTS idx_roster_entries_org ON roster_entries(organization_id);
CREATE INDEX IF NOT EXISTS idx_roster_entries_date ON roster_entries(work_date);

CREATE TABLE IF NOT EXISTS allowance_types (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    code            VARCHAR(40) NOT NULL,
    amount          DECIMAL(15,2) NOT NULL DEFAULT 0,
    frequency       VARCHAR(20) NOT NULL DEFAULT 'monthly'
                    CHECK (frequency IN ('monthly', 'one_time')),
    taxable         BOOLEAN NOT NULL DEFAULT TRUE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    description     TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_allowance_types_org ON allowance_types(organization_id);

CREATE TABLE IF NOT EXISTS branches (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    city            VARCHAR(100),
    address         TEXT,
    headcount       INTEGER NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_branches_org ON branches(organization_id);

CREATE TABLE IF NOT EXISTS employee_family (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    full_name       VARCHAR(160) NOT NULL,
    relationship    VARCHAR(80),
    date_of_birth   DATE,
    phone           VARCHAR(40),
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_employee_family_org ON employee_family(organization_id);
CREATE INDEX IF NOT EXISTS idx_employee_family_employee ON employee_family(employee_id);

CREATE TABLE IF NOT EXISTS employee_education (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    institution     VARCHAR(200) NOT NULL,
    degree          VARCHAR(160),
    field_of_study  VARCHAR(160),
    start_year      INTEGER,
    end_year        INTEGER,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_employee_education_org ON employee_education(organization_id);
CREATE INDEX IF NOT EXISTS idx_employee_education_employee ON employee_education(employee_id);
