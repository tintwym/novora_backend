-- Approvals / claims receipts & policy / leave accrual & attachments (web ops).

ALTER TABLE expense_claims
    ADD COLUMN IF NOT EXISTS receipt_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS receipt_content_base64 TEXT,
    ADD COLUMN IF NOT EXISTS receipt_url TEXT;

CREATE TABLE IF NOT EXISTS claim_policies (
    organization_id          UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    receipt_required_above   DECIMAL(15,2) NOT NULL DEFAULT 50.00,
    max_claim_age_days       INTEGER NOT NULL DEFAULT 30,
    block_future_dates       BOOLEAN NOT NULL DEFAULT TRUE,
    enforce_receipt          BOOLEAN NOT NULL DEFAULT TRUE,
    enforce_claim_age        BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at               TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS claim_category_limits (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    category          VARCHAR(80) NOT NULL,
    daily_limit       DECIMAL(15,2),
    monthly_limit     DECIMAL(15,2),
    receipt_required  VARCHAR(20) NOT NULL DEFAULT 'THRESHOLD'
                      CHECK (receipt_required IN ('ALWAYS', 'THRESHOLD', 'NEVER')),
    created_at        TIMESTAMP DEFAULT NOW(),
    UNIQUE (organization_id, category)
);

CREATE INDEX IF NOT EXISTS idx_claim_category_limits_org ON claim_category_limits(organization_id);

ALTER TABLE leave_types
    ADD COLUMN IF NOT EXISTS requires_attachment BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS accrual_method VARCHAR(30) NOT NULL DEFAULT 'ANNUAL'
        CHECK (accrual_method IN ('ANNUAL', 'MONTHLY_PRORATE'));

ALTER TABLE leave_requests
    ADD COLUMN IF NOT EXISTS attachment_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS attachment_content_base64 TEXT;
