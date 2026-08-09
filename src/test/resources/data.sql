-- Minimal seed for integration tests (Hibernate ddl-auto creates schema first).
-- Multi-tenancy: every row needs an organization_id since Hibernate's @TenantId column is NOT NULL.
-- We seed the implicit "Novora Internal" org here so this script matches what
-- ReferenceDataSeeder does for fresh production databases.
INSERT INTO organizations (id, name, slug, plan, status, created_at, updated_at)
SELECT RANDOM_UUID(), 'Novora Internal', 'novora-internal', 'ENTERPRISE', 'ACTIVE',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM organizations WHERE slug = 'novora-internal');

INSERT INTO departments (id, organization_id, name, code, description, is_active, created_at, updated_at)
SELECT RANDOM_UUID(),
       (SELECT id FROM organizations WHERE slug = 'novora-internal'),
       'Engineering', 'ENG', 'Test', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM departments);

INSERT INTO positions (id, organization_id, title, department_id, level, is_active, created_at, updated_at)
SELECT RANDOM_UUID(),
       (SELECT id FROM organizations WHERE slug = 'novora-internal'),
       'Team Member',
       (SELECT id FROM departments LIMIT 1),
       'mid', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM positions);

INSERT INTO leave_types (id, organization_id, code, name, label, display_color, sort_order,
                         company_pool_days, days_allowed, is_paid, carry_forward, max_carry_days,
                         description, is_active, created_at)
SELECT RANDOM_UUID(),
       (SELECT id FROM organizations WHERE slug = 'novora-internal'),
       'ANNUAL', 'Annual Leave', 'Annual Leave', '#4f8dff', 1, 200, 14, true, false, 0, 'Test',
       true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM leave_types WHERE lower(name) = 'annual leave');
