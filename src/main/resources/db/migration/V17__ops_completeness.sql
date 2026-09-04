-- Ops completeness: helpdesk, disciplinary, benefits, onboarding, time logs, document inline content.

ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS content_base64 TEXT;

-- Allow claim notifications (and keep existing types).
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IS NULL OR type IN (
        'leave', 'payroll', 'attendance', 'performance', 'training',
        'announcement', 'system', 'claim'
    ));

CREATE TABLE IF NOT EXISTS helpdesk_tickets (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id       UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    subject               VARCHAR(255) NOT NULL,
    description           TEXT,
    category              VARCHAR(80),
    priority              VARCHAR(40),
    status                VARCHAR(40) NOT NULL DEFAULT 'open',
    requester_employee_id UUID REFERENCES employees(id) ON DELETE SET NULL,
    assignee_employee_id  UUID REFERENCES employees(id) ON DELETE SET NULL,
    created_at            TIMESTAMP DEFAULT NOW(),
    updated_at            TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_helpdesk_tickets_org ON helpdesk_tickets(organization_id);
CREATE INDEX IF NOT EXISTS idx_helpdesk_tickets_requester ON helpdesk_tickets(requester_employee_id);
CREATE INDEX IF NOT EXISTS idx_helpdesk_tickets_status ON helpdesk_tickets(status);

CREATE TABLE IF NOT EXISTS helpdesk_replies (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticket_id          UUID NOT NULL REFERENCES helpdesk_tickets(id) ON DELETE CASCADE,
    author_employee_id UUID REFERENCES employees(id) ON DELETE SET NULL,
    body               TEXT NOT NULL,
    created_at         TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_helpdesk_replies_ticket ON helpdesk_replies(ticket_id);

CREATE TABLE IF NOT EXISTS disciplinary_cases (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    reason          VARCHAR(255) NOT NULL,
    action_type     VARCHAR(80),
    severity        VARCHAR(40),
    status          VARCHAR(40) NOT NULL DEFAULT 'open',
    notes           TEXT,
    incident_date   DATE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_disciplinary_cases_org ON disciplinary_cases(organization_id);
CREATE INDEX IF NOT EXISTS idx_disciplinary_cases_employee ON disciplinary_cases(employee_id);

CREATE TABLE IF NOT EXISTS benefit_plans (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name             VARCHAR(200) NOT NULL,
    category         VARCHAR(80),
    provider         VARCHAR(160),
    coverage_summary TEXT,
    employee_cost    DECIMAL(15,2),
    employer_cost    DECIMAL(15,2),
    status           VARCHAR(40) NOT NULL DEFAULT 'active',
    created_at       TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_benefit_plans_org ON benefit_plans(organization_id);

CREATE TABLE IF NOT EXISTS benefit_enrollments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    plan_id         UUID NOT NULL REFERENCES benefit_plans(id) ON DELETE CASCADE,
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    status          VARCHAR(40) NOT NULL DEFAULT 'enrolled',
    enrolled_at     TIMESTAMP DEFAULT NOW(),
    notes           TEXT
);

CREATE INDEX IF NOT EXISTS idx_benefit_enrollments_org ON benefit_enrollments(organization_id);
CREATE INDEX IF NOT EXISTS idx_benefit_enrollments_employee ON benefit_enrollments(employee_id);
CREATE INDEX IF NOT EXISTS idx_benefit_enrollments_plan ON benefit_enrollments(plan_id);

CREATE TABLE IF NOT EXISTS onboarding_tasks (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    due_date        DATE,
    status          VARCHAR(40) NOT NULL DEFAULT 'pending',
    sort_order      INTEGER NOT NULL DEFAULT 0,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_onboarding_tasks_org ON onboarding_tasks(organization_id);
CREATE INDEX IF NOT EXISTS idx_onboarding_tasks_employee ON onboarding_tasks(employee_id);

CREATE TABLE IF NOT EXISTS time_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    work_date       DATE NOT NULL,
    hours           DECIMAL(8,2) NOT NULL DEFAULT 0,
    project         VARCHAR(160),
    notes           TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_time_logs_org ON time_logs(organization_id);
CREATE INDEX IF NOT EXISTS idx_time_logs_employee ON time_logs(employee_id);
CREATE INDEX IF NOT EXISTS idx_time_logs_work_date ON time_logs(work_date);
