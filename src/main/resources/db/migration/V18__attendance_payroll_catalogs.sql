-- Attendance + payroll catalog tables (bonus, deduction, deposit, tax, OT).

CREATE TABLE IF NOT EXISTS bonus_types (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    code            VARCHAR(40) NOT NULL,
    amount          DECIMAL(15,2) NOT NULL DEFAULT 0,
    taxable         BOOLEAN NOT NULL DEFAULT TRUE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    description     TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (organization_id, code)
);

CREATE INDEX IF NOT EXISTS idx_bonus_types_org ON bonus_types(organization_id);

CREATE TABLE IF NOT EXISTS deduction_types (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    code            VARCHAR(40) NOT NULL,
    amount          DECIMAL(15,2) NOT NULL DEFAULT 0,
    frequency       VARCHAR(20) NOT NULL DEFAULT 'monthly'
                    CHECK (frequency IN ('monthly', 'one_time')),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    description     TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (organization_id, code)
);

CREATE INDEX IF NOT EXISTS idx_deduction_types_org ON deduction_types(organization_id);

CREATE TABLE IF NOT EXISTS deposit_types (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    code            VARCHAR(40) NOT NULL,
    amount          DECIMAL(15,2) NOT NULL DEFAULT 0,
    refundable      BOOLEAN NOT NULL DEFAULT FALSE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    description     TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (organization_id, code)
);

CREATE INDEX IF NOT EXISTS idx_deposit_types_org ON deposit_types(organization_id);

CREATE TABLE IF NOT EXISTS tax_categories (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    code            VARCHAR(40) NOT NULL,
    rate            DECIMAL(7,4) NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    description     TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (organization_id, code)
);

CREATE INDEX IF NOT EXISTS idx_tax_categories_org ON tax_categories(organization_id);

CREATE TABLE IF NOT EXISTS ot_policies (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id       UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name                  VARCHAR(120) NOT NULL,
    weekday_multiplier    DECIMAL(4,2) NOT NULL DEFAULT 1.5,
    weekend_multiplier    DECIMAL(4,2) NOT NULL DEFAULT 2.0,
    holiday_multiplier    DECIMAL(4,2) NOT NULL DEFAULT 3.0,
    daily_threshold_hours DECIMAL(4,2) NOT NULL DEFAULT 8,
    requires_approval     BOOLEAN NOT NULL DEFAULT TRUE,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    notes                 TEXT,
    created_at            TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ot_policies_org ON ot_policies(organization_id);

CREATE TABLE IF NOT EXISTS overtime_records (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    work_date       DATE NOT NULL,
    start_time      VARCHAR(10),
    end_time        VARCHAR(10),
    hours           DECIMAL(4,2) NOT NULL DEFAULT 0,
    reason          TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled')),
    decided_by      UUID,
    decision_note   TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_overtime_records_org ON overtime_records(organization_id);
CREATE INDEX IF NOT EXISTS idx_overtime_records_employee ON overtime_records(employee_id);
CREATE INDEX IF NOT EXISTS idx_overtime_records_date ON overtime_records(work_date);
CREATE INDEX IF NOT EXISTS idx_overtime_records_status ON overtime_records(status);
