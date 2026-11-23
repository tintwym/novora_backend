-- Novora Accounting ERP link: map this HRMS workspace to an Accounting company UUID.

CREATE TABLE IF NOT EXISTS accounting_connections (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    accounting_company_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_payroll_run_id VARCHAR(80),
    last_synced_at TIMESTAMP,
    last_error VARCHAR(1000),
    connected_at TIMESTAMP NOT NULL,
    disconnected_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
