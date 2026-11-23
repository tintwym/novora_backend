-- Per-employee compensation used by payroll generation (replaces fixed defaults).

ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS basic_salary DECIMAL(15, 2),
    ADD COLUMN IF NOT EXISTS monthly_allowance DECIMAL(15, 2),
    ADD COLUMN IF NOT EXISTS monthly_deduction DECIMAL(15, 2),
    ADD COLUMN IF NOT EXISTS tax_rate_percent DECIMAL(7, 4);

COMMENT ON COLUMN employees.basic_salary IS 'Monthly basic salary for payroll runs';
COMMENT ON COLUMN employees.monthly_allowance IS 'Fixed monthly allowance override; null = sum active monthly allowance_types';
COMMENT ON COLUMN employees.monthly_deduction IS 'Fixed monthly deduction override; null = sum active monthly deduction_types';
COMMENT ON COLUMN employees.tax_rate_percent IS 'Tax percent override (e.g. 8.0000); null = first active tax_categories.rate';
