-- Novora HRMS schema v1.1.0 — replaces prior Flyway tables (V9 and extensions).
-- Drops application tables in dependency-safe order, then creates full schema + seeds.

DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS announcements CASCADE;
DROP TABLE IF EXISTS interviews CASCADE;
DROP TABLE IF EXISTS candidates CASCADE;
DROP TABLE IF EXISTS job_postings CASCADE;
DROP TABLE IF EXISTS payroll_components CASCADE;
DROP TABLE IF EXISTS hr_payroll CASCADE;
DROP TABLE IF EXISTS payroll CASCADE;
DROP TABLE IF EXISTS holidays CASCADE;
DROP TABLE IF EXISTS leave_requests CASCADE;
DROP TABLE IF EXISTS leave_balances CASCADE;
DROP TABLE IF EXISTS attendance CASCADE;
DROP TABLE IF EXISTS documents CASCADE;
DROP TABLE IF EXISTS assets CASCADE;
DROP TABLE IF EXISTS training_enrollments CASCADE;
DROP TABLE IF EXISTS training CASCADE;
DROP TABLE IF EXISTS performance_reviews CASCADE;
DROP TABLE IF EXISTS employees CASCADE;
DROP TABLE IF EXISTS positions CASCADE;
DROP TABLE IF EXISTS departments CASCADE;
DROP TABLE IF EXISTS leave_types CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS employee_document CASCADE;
DROP TABLE IF EXISTS feed_post CASCADE;
DROP TABLE IF EXISTS approval_task CASCADE;
DROP TABLE IF EXISTS onboarding_task CASCADE;
DROP TABLE IF EXISTS time_log CASCADE;
DROP TABLE IF EXISTS employee_education CASCADE;
DROP TABLE IF EXISTS employee_family CASCADE;
DROP TABLE IF EXISTS employee_personal CASCADE;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       TEXT NOT NULL,
    role                VARCHAR(20) NOT NULL CHECK (role IN ('SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER', 'MANAGER', 'EMPLOYEE')),
    is_active           BOOLEAN DEFAULT TRUE,
    is_email_verified   BOOLEAN DEFAULT FALSE,
    last_login          TIMESTAMP,
    password_reset_token TEXT,
    password_reset_exp  TIMESTAMP,
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

CREATE TABLE departments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(100) NOT NULL,
    code            VARCHAR(20) NOT NULL UNIQUE,
    description     TEXT,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE positions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title           VARCHAR(100) NOT NULL,
    department_id   UUID NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    level           VARCHAR(20) CHECK (level IN ('junior', 'mid', 'senior', 'lead', 'manager', 'director', 'c_level')),
    min_salary      DECIMAL(15,2),
    max_salary      DECIMAL(15,2),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE employees (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    department_id       UUID NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    position_id         UUID NOT NULL REFERENCES positions(id) ON DELETE RESTRICT,
    manager_id          UUID REFERENCES employees(id) ON DELETE SET NULL,
    employee_code       VARCHAR(20) NOT NULL UNIQUE,
    email               VARCHAR(255) NOT NULL UNIQUE,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    gender              VARCHAR(10) CHECK (gender IN ('male', 'female', 'other')),
    date_of_birth       DATE,
    phone               VARCHAR(20),
    emergency_contact   VARCHAR(100),
    emergency_phone     VARCHAR(20),
    address             TEXT,
    city                VARCHAR(100),
    state               VARCHAR(100),
    country             VARCHAR(100),
    postal_code         VARCHAR(20),
    profile_photo       TEXT,
    hire_date           DATE NOT NULL,
    end_date            DATE,
    employment_type     VARCHAR(20) CHECK (employment_type IN ('full_time', 'part_time', 'contract', 'intern')),
    status              VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'inactive', 'terminated', 'on_leave', 'suspended')),
    bank_name           VARCHAR(100),
    bank_account        VARCHAR(50),
    tax_id              VARCHAR(50),
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

ALTER TABLE departments ADD COLUMN manager_id UUID REFERENCES employees(id) ON DELETE SET NULL;

CREATE TABLE attendance (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    work_date       DATE NOT NULL,
    check_in        TIMESTAMP,
    check_out       TIMESTAMP,
    work_hours      DECIMAL(4,2),
    overtime_hours  DECIMAL(4,2) DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'present' CHECK (status IN ('present', 'absent', 'late', 'half_day', 'on_leave', 'holiday', 'weekend')),
    location        VARCHAR(100),
    notes           TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (employee_id, work_date)
);

CREATE TABLE leave_types (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(100) NOT NULL UNIQUE,
    code            VARCHAR(30) NOT NULL UNIQUE,
    label           VARCHAR(128) NOT NULL,
    display_color   VARCHAR(16),
    sort_order      INTEGER NOT NULL DEFAULT 0,
    company_pool_days INTEGER NOT NULL DEFAULT 0,
    days_allowed    INTEGER NOT NULL,
    is_paid         BOOLEAN DEFAULT TRUE,
    carry_forward   BOOLEAN DEFAULT FALSE,
    max_carry_days  INTEGER DEFAULT 0,
    description     TEXT,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE leave_balances (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    leave_type_id   UUID NOT NULL REFERENCES leave_types(id) ON DELETE CASCADE,
    balance_year    INTEGER NOT NULL,
    total_days      DECIMAL(5,2) NOT NULL,
    used_days       DECIMAL(5,2) DEFAULT 0,
    pending_days    DECIMAL(5,2) DEFAULT 0,
    remaining_days  DECIMAL(5,2),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (employee_id, leave_type_id, balance_year)
);

CREATE TABLE leave_requests (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    leave_type_id   UUID NOT NULL REFERENCES leave_types(id) ON DELETE RESTRICT,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    total_days      DECIMAL(5,2) NOT NULL,
    reason          TEXT,
    attachment_url  TEXT,
    status          VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled')),
    approved_by     UUID REFERENCES employees(id) ON DELETE SET NULL,
    approved_at     TIMESTAMP,
    rejection_note  TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE holidays (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(100) NOT NULL,
    holiday_date    DATE NOT NULL UNIQUE,
    type            VARCHAR(20) CHECK (type IN ('public', 'company', 'optional')),
    description     TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE hr_payroll (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    pay_month       INTEGER NOT NULL CHECK (pay_month BETWEEN 1 AND 12),
    pay_year        INTEGER NOT NULL,
    basic_salary    DECIMAL(15,2) NOT NULL,
    allowances      DECIMAL(15,2) DEFAULT 0,
    overtime_pay    DECIMAL(15,2) DEFAULT 0,
    bonus           DECIMAL(15,2) DEFAULT 0,
    deductions      DECIMAL(15,2) DEFAULT 0,
    tax             DECIMAL(15,2) DEFAULT 0,
    net_pay         DECIMAL(15,2) NOT NULL,
    working_days    INTEGER,
    present_days    INTEGER,
    absent_days     INTEGER,
    status          VARCHAR(20) DEFAULT 'draft' CHECK (status IN ('draft', 'processed', 'paid', 'cancelled')),
    processed_by    UUID REFERENCES employees(id) ON DELETE SET NULL,
    processed_at    TIMESTAMP,
    paid_at         TIMESTAMP,
    notes           TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (employee_id, pay_month, pay_year)
);

CREATE TABLE payroll_components (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payroll_id      UUID NOT NULL REFERENCES hr_payroll(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(20) CHECK (type IN ('allowance', 'deduction', 'bonus', 'tax')),
    amount          DECIMAL(15,2) NOT NULL,
    description     TEXT
);

CREATE TABLE performance_reviews (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    reviewer_id     UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    review_year     INTEGER NOT NULL,
    review_quarter  INTEGER CHECK (review_quarter BETWEEN 1 AND 4),
    review_type     VARCHAR(20) CHECK (review_type IN ('quarterly', 'annual', 'probation', 'ad_hoc')),
    score           DECIMAL(4,2) CHECK (score BETWEEN 0 AND 10),
    rating          VARCHAR(20) CHECK (rating IN ('excellent', 'good', 'satisfactory', 'needs_improvement', 'unsatisfactory')),
    goals           TEXT,
    achievements    TEXT,
    areas_to_improve TEXT,
    comments        TEXT,
    employee_comment TEXT,
    status          VARCHAR(20) DEFAULT 'draft' CHECK (status IN ('draft', 'submitted', 'acknowledged', 'completed')),
    submitted_at    TIMESTAMP,
    acknowledged_at TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE training (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    category        VARCHAR(100),
    trainer         VARCHAR(100),
    trainer_type    VARCHAR(20) CHECK (trainer_type IN ('internal', 'external')),
    location        VARCHAR(200),
    mode            VARCHAR(20) CHECK (mode IN ('online', 'offline', 'hybrid')),
    start_date      DATE,
    end_date        DATE,
    duration_hours  DECIMAL(5,2),
    max_participants INTEGER,
    cost            DECIMAL(15,2),
    materials_url   TEXT,
    status          VARCHAR(20) DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'ongoing', 'completed', 'cancelled')),
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE training_enrollments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    training_id     UUID NOT NULL REFERENCES training(id) ON DELETE CASCADE,
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    enrolled_at     TIMESTAMP DEFAULT NOW(),
    completed_at    TIMESTAMP,
    score           DECIMAL(5,2),
    status          VARCHAR(20) DEFAULT 'enrolled' CHECK (status IN ('enrolled', 'in_progress', 'completed', 'dropped', 'failed')),
    certificate_url TEXT,
    feedback        TEXT,
    UNIQUE (training_id, employee_id)
);

CREATE TABLE assets (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(200) NOT NULL,
    asset_code      VARCHAR(50) NOT NULL UNIQUE,
    category        VARCHAR(50) CHECK (category IN ('laptop', 'phone', 'tablet', 'vehicle', 'furniture', 'equipment', 'other')),
    brand           VARCHAR(100),
    model           VARCHAR(100),
    serial_number   VARCHAR(100),
    purchase_date   DATE,
    purchase_price  DECIMAL(15,2),
    warranty_expiry DATE,
    assigned_to     UUID REFERENCES employees(id) ON DELETE SET NULL,
    assigned_date   DATE,
    return_date     DATE,
    condition       VARCHAR(20) DEFAULT 'good' CHECK (condition IN ('new', 'good', 'fair', 'poor', 'damaged', 'disposed')),
    location        VARCHAR(100),
    notes           TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    type            VARCHAR(50) CHECK (type IN ('contract', 'id_card', 'certificate', 'payslip', 'offer_letter', 'nda', 'warning_letter', 'appraisal', 'other')),
    file_url        TEXT NOT NULL,
    file_size       BIGINT,
    file_type       VARCHAR(20),
    is_confidential BOOLEAN DEFAULT FALSE,
    expiry_date     DATE,
    uploaded_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at     TIMESTAMP DEFAULT NOW()
);

CREATE TABLE job_postings (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    position_id     UUID NOT NULL REFERENCES positions(id) ON DELETE RESTRICT,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    requirements    TEXT,
    responsibilities TEXT,
    location        VARCHAR(100),
    employment_type VARCHAR(20),
    salary_min      DECIMAL(15,2),
    salary_max      DECIMAL(15,2),
    open_date       DATE DEFAULT CURRENT_DATE,
    close_date      DATE,
    openings        INTEGER DEFAULT 1,
    is_published    BOOLEAN DEFAULT FALSE,
    status          VARCHAR(20) DEFAULT 'draft' CHECK (status IN ('draft', 'open', 'closed', 'on_hold', 'filled')),
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE candidates (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_posting_id  UUID NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    full_name       VARCHAR(200) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    phone           VARCHAR(20),
    resume_url      TEXT,
    cover_letter    TEXT,
    portfolio_url   TEXT,
    source          VARCHAR(50) CHECK (source IN ('website', 'linkedin', 'referral', 'agency', 'walk_in', 'other')),
    referred_by     UUID REFERENCES employees(id) ON DELETE SET NULL,
    stage           VARCHAR(30) DEFAULT 'applied' CHECK (stage IN ('applied', 'screening', 'interview', 'technical', 'hr_interview', 'offer', 'hired', 'rejected')),
    status          VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'withdrawn', 'rejected', 'hired')),
    rating          INTEGER CHECK (rating BETWEEN 1 AND 5),
    notes           TEXT,
    applied_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE interviews (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    candidate_id    UUID NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    interviewer_id  UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    scheduled_at    TIMESTAMP NOT NULL,
    duration_mins   INTEGER DEFAULT 60,
    mode            VARCHAR(20) CHECK (mode IN ('in_person', 'video', 'phone')),
    location        VARCHAR(200),
    meet_link       TEXT,
    round           VARCHAR(30) CHECK (round IN ('screening', 'technical', 'hr', 'final', 'other')),
    status          VARCHAR(20) DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'completed', 'cancelled', 'no_show')),
    feedback        TEXT,
    score           INTEGER CHECK (score BETWEEN 1 AND 10),
    recommendation  VARCHAR(20) CHECK (recommendation IN ('strong_hire', 'hire', 'neutral', 'no_hire', 'strong_no_hire')),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE announcements (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    type            VARCHAR(30) CHECK (type IN ('general', 'hr', 'event', 'policy', 'urgent')),
    target_role     VARCHAR(20) CHECK (target_role IN ('ALL', 'SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER', 'MANAGER', 'EMPLOYEE')),
    department_id   UUID REFERENCES departments(id) ON DELETE SET NULL,
    is_pinned       BOOLEAN DEFAULT FALSE,
    published_at    TIMESTAMP,
    expires_at      TIMESTAMP,
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    message         TEXT,
    type            VARCHAR(30) CHECK (type IN ('leave', 'payroll', 'attendance', 'performance', 'training', 'announcement', 'system')),
    reference_id    UUID,
    reference_type  VARCHAR(50),
    is_read         BOOLEAN DEFAULT FALSE,
    read_at         TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(50) NOT NULL,
    table_name      VARCHAR(50),
    record_id       UUID,
    old_values      JSONB,
    new_values      JSONB,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_email               ON users(email);
CREATE INDEX idx_users_role                ON users(role);
CREATE INDEX idx_employees_user            ON employees(user_id);
CREATE INDEX idx_employees_department      ON employees(department_id);
CREATE INDEX idx_employees_position        ON employees(position_id);
CREATE INDEX idx_employees_manager         ON employees(manager_id);
CREATE INDEX idx_employees_status          ON employees(status);
CREATE INDEX idx_employees_code            ON employees(employee_code);
CREATE INDEX idx_employees_email           ON employees(email);
CREATE INDEX idx_attendance_employee       ON attendance(employee_id);
CREATE INDEX idx_attendance_date           ON attendance(work_date);
CREATE INDEX idx_attendance_status         ON attendance(status);
CREATE INDEX idx_leave_requests_employee   ON leave_requests(employee_id);
CREATE INDEX idx_leave_requests_status     ON leave_requests(status);
CREATE INDEX idx_leave_requests_dates      ON leave_requests(start_date, end_date);
CREATE INDEX idx_leave_balances_employee   ON leave_balances(employee_id);
CREATE INDEX idx_payroll_employee          ON hr_payroll(employee_id);
CREATE INDEX idx_payroll_period            ON hr_payroll(pay_year, pay_month);
CREATE INDEX idx_payroll_status            ON hr_payroll(status);
CREATE INDEX idx_performance_employee      ON performance_reviews(employee_id);
CREATE INDEX idx_performance_period        ON performance_reviews(review_year, review_quarter);
CREATE INDEX idx_training_status           ON training(status);
CREATE INDEX idx_training_enroll_employee  ON training_enrollments(employee_id);
CREATE INDEX idx_assets_assigned           ON assets(assigned_to);
CREATE INDEX idx_documents_employee        ON documents(employee_id);
CREATE INDEX idx_job_postings_status       ON job_postings(status);
CREATE INDEX idx_candidates_posting        ON candidates(job_posting_id);
CREATE INDEX idx_candidates_stage          ON candidates(stage);
CREATE INDEX idx_interviews_candidate      ON interviews(candidate_id);
CREATE INDEX idx_notifications_user        ON notifications(user_id);
CREATE INDEX idx_notifications_read        ON notifications(is_read);
CREATE INDEX idx_audit_logs_user           ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_table          ON audit_logs(table_name);
CREATE INDEX idx_announcements_role        ON announcements(target_role);

INSERT INTO leave_types (code, name, label, display_color, sort_order, company_pool_days, days_allowed, is_paid, carry_forward, max_carry_days, description) VALUES
  ('ANNUAL',    'Annual Leave',    'Annual Leave',    '#4f8dff', 1, 200, 14, TRUE,  TRUE,  5,  'Yearly paid vacation leave'),
  ('SICK',      'Sick Leave',      'Sick Leave',      '#7c3aed', 2, 100,  7, TRUE,  FALSE, 0,  'Medical or illness leave'),
  ('PERSONAL',  'Personal Leave',  'Personal Leave',  '#a78bfa', 3,  60,  3, TRUE,  FALSE, 0,  'Personal matters leave'),
  ('MATERNITY', 'Maternity Leave', 'Maternity Leave', '#c4b5fd', 4,  40, 90, TRUE,  FALSE, 0,  'Paid maternity leave'),
  ('PATERNITY', 'Paternity Leave', 'Paternity Leave', '#818cf8', 5,  40, 14, TRUE,  FALSE, 0,  'Paid paternity leave'),
  ('UNPAID',    'Unpaid Leave',    'Unpaid Leave',    '#94a3b8', 6,   0, 30, FALSE, FALSE, 0,  'Unpaid extended leave'),
  ('EMERGENCY', 'Emergency Leave', 'Emergency Leave', '#f472b6', 7,  20,  3, TRUE,  FALSE, 0,  'Immediate family emergency');

INSERT INTO departments (name, code, description) VALUES
  ('Human Resources',   'HR',  'HR operations and people management'),
  ('Engineering',       'ENG', 'Software development and infrastructure'),
  ('Sales',             'SLS', 'Sales and business development'),
  ('Marketing',         'MKT', 'Marketing and communications'),
  ('Finance',           'FIN', 'Finance and accounting'),
  ('Operations',        'OPS', 'General operations and admin');

INSERT INTO positions (title, department_id, level, is_active)
SELECT 'Team Member', d.id, 'mid', true FROM departments d;

-- The super-admin user is provisioned at runtime by BootstrapAdminConfiguration / AdminUserService,
-- which reads APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD from the environment.
-- Do NOT seed an admin row here: a hardcoded password (especially a well-known one) becomes a
-- public default credential the day this migration is replayed against a fresh database.
