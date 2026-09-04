-- Expense claims for employee reimbursements (Phase 2 Path B).
CREATE TABLE IF NOT EXISTS expense_claims (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    category        VARCHAR(80) NOT NULL,
    claim_date      DATE NOT NULL,
    amount          DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3) NOT NULL DEFAULT 'SGD',
    vendor          VARCHAR(200),
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled')),
    decision_note   TEXT,
    decided_by      UUID REFERENCES employees(id) ON DELETE SET NULL,
    decided_at      TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_expense_claims_org ON expense_claims(organization_id);
CREATE INDEX IF NOT EXISTS idx_expense_claims_employee ON expense_claims(employee_id);
CREATE INDEX IF NOT EXISTS idx_expense_claims_status ON expense_claims(status);
