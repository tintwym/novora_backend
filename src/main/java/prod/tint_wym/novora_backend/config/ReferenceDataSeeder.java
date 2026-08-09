package prod.tint_wym.novora_backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import prod.tint_wym.novora_backend.entity.Department;
import prod.tint_wym.novora_backend.entity.LeaveType;
import prod.tint_wym.novora_backend.entity.Organization;
import prod.tint_wym.novora_backend.entity.Position;
import prod.tint_wym.novora_backend.repository.DepartmentRepository;
import prod.tint_wym.novora_backend.repository.LeaveTypeRepository;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;
import prod.tint_wym.novora_backend.repository.PositionRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

/**
 * Boots the database into a usable state when Hibernate {@code ddl-auto=update} has just created
 * empty tables, and migrates legacy single-tenant data into the multi-tenant layout.
 *
 * <p>Order of operations:
 * <ol>
 *   <li>Ensure the implicit "Novora Internal" organization exists (plan=ENTERPRISE so it is
 *       never expired by the trial sweeper).</li>
 *   <li>Backfill {@code organization_id} on every legacy row that pre-dates the multi-tenancy
 *       column. After this step every row in the system has a non-null tenant id, even though
 *       the columns remain JPA-nullable until a future migration tightens them.</li>
 *   <li>Seed default departments / positions / leave types for the Internal org if they're not
 *       already there (preserving the V10 seed list for fresh databases).</li>
 * </ol>
 */
@Component
@Order(0)
public class ReferenceDataSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ReferenceDataSeeder.class);
    private static final String INTERNAL_ORG_SLUG = "novora-internal";
    private static final String INTERNAL_ORG_NAME = "Novora Internal";

    /**
     * Tables that need a tenant id backfilled. Kept as raw SQL because Hibernate refuses to
     * UPDATE a {@code @TenantId} column via JPQL — the resolver would re-stamp it on every row
     * with the current request's tenant, which is exactly the wrong behavior here.
     *
     * <p>{@code users} is included even though AppUser is not {@code @TenantId}-scoped, because
     * the column was added by Hibernate from the entity definition.
     */
    private static final List<String> TENANT_BACKFILL_TABLES = List.of(
            "users",
            "departments",
            "positions",
            "employees",
            "leave_types",
            "holidays",
            "training",
            "assets",
            "documents",
            "job_postings",
            "candidates",
            "announcements",
            "audit_logs");

    private final OrganizationRepository organizationRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public ReferenceDataSeeder(
            OrganizationRepository organizationRepository,
            DepartmentRepository departmentRepository,
            PositionRepository positionRepository,
            LeaveTypeRepository leaveTypeRepository,
            PlatformTransactionManager transactionManager) {
        this.organizationRepository = organizationRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        // We deliberately don't put @Transactional on this method. Hibernate's @TenantId resolver
        // is queried at session-open time and the result is cached for the whole session — so if
        // we open a transaction before TenantContext is set, every save inside it will fail with
        // "assigned tenant id differs from current tenant id". Instead we run each logical phase
        // in its own transaction *after* the correct tenant has been pushed into TenantContext.
        UUID priorTenant = TenantContext.get();
        TenantContext.clear();
        Organization internal;
        try {
            internal = transactionTemplate.execute(status -> ensureInternalOrganization());
            int backfilled = backfillNullOrganizationIds(internal.getId());
            if (backfilled > 0) {
                LOG.info(
                        "ReferenceDataSeeder: backfilled organization_id={} on {} legacy row(s)",
                        internal.getId(),
                        backfilled);
            }
        } finally {
            if (priorTenant != null) {
                TenantContext.set(priorTenant);
            }
        }

        TenantContext.set(internal.getId());
        try {
            transactionTemplate.execute(status -> {
                seedInternalReferenceData(internal);
                return null;
            });
        } finally {
            if (priorTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(priorTenant);
            }
        }

        seedMissingLeaveTypesForAllOrganizations();
    }

    /**
     * Workspaces created before leave-type seeding was added to signup would have zero leave
     * types and could not submit leave requests. Backfill any org that is still missing them.
     */
    private void seedMissingLeaveTypesForAllOrganizations() {
        UUID priorTenant = TenantContext.get();
        for (Organization org : organizationRepository.findAll()) {
            TenantContext.set(org.getId());
            try {
                transactionTemplate.execute(status -> {
                    int added = seedLeaveTypes(org);
                    if (added > 0) {
                        LOG.info(
                                "ReferenceDataSeeder: seeded {} default leave type(s) for org '{}' ({})",
                                added,
                                org.getName(),
                                org.getId());
                    }
                    return null;
                });
            } finally {
                if (priorTenant == null) {
                    TenantContext.clear();
                } else {
                    TenantContext.set(priorTenant);
                }
            }
        }
    }

    private Organization ensureInternalOrganization() {
        return organizationRepository.findBySlug(INTERNAL_ORG_SLUG)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    Organization org = new Organization();
                    org.setName(INTERNAL_ORG_NAME);
                    org.setSlug(INTERNAL_ORG_SLUG);
                    org.setPlan(Organization.Plan.ENTERPRISE);
                    org.setStatus(Organization.Status.ACTIVE);
                    org.setCreatedAt(now);
                    org.setUpdatedAt(now);
                    Organization saved = organizationRepository.save(org);
                    LOG.info(
                            "ReferenceDataSeeder: created '{}' org (id={}) for legacy data",
                            INTERNAL_ORG_NAME,
                            saved.getId());
                    return saved;
                });
    }

    /**
     * Returns the cumulative number of rows updated. Uses native UPDATE ... WHERE org_id IS NULL
     * so it's idempotent: re-running the seeder against an already-backfilled DB is a cheap no-op.
     */
    private int backfillNullOrganizationIds(UUID internalOrgId) {
        int total = 0;
        for (String table : TENANT_BACKFILL_TABLES) {
            Integer updated = transactionTemplate.execute(status -> {
                try {
                    return entityManager.createNativeQuery(
                                    "UPDATE " + table
                                            + " SET organization_id = :orgId WHERE organization_id IS NULL")
                            .setParameter("orgId", internalOrgId)
                            .executeUpdate();
                } catch (RuntimeException ex) {
                    // Column may not exist yet on a partially-migrated DB; Flyway V11 should have
                    // added it, but log and continue so other tables can still be backfilled.
                    LOG.warn(
                            "ReferenceDataSeeder: could not backfill '{}' (likely column missing): {}",
                            table,
                            ex.getMessage());
                    return 0;
                }
            });
            total += updated != null ? updated : 0;
        }
        return total;
    }

    private void seedInternalReferenceData(Organization internal) {
        if (departmentRepository.count() == 0) {
            seedDefaultDepartmentsAndPositions(internal);
        }
        seedLeaveTypes(internal);
    }

    private void seedDefaultDepartmentsAndPositions(Organization internal) {
        LocalDateTime now = LocalDateTime.now();
        List<String[]> deptRows = List.of(
                new String[] {"Human Resources", "HR", "HR operations and people management"},
                new String[] {"Engineering", "ENG", "Software development and infrastructure"},
                new String[] {"Sales", "SLS", "Sales and business development"},
                new String[] {"Marketing", "MKT", "Marketing and communications"},
                new String[] {"Finance", "FIN", "Finance and accounting"},
                new String[] {"Operations", "OPS", "General operations and admin"});

        for (String[] row : deptRows) {
            Department d = new Department();
            d.setName(row[0]);
            d.setCode(row[1]);
            d.setDescription(row[2]);
            d.setActive(true);
            d.setCreatedAt(now);
            d.setUpdatedAt(now);
            d.setOrganizationId(internal.getId());
            d = departmentRepository.save(d);

            Position p = new Position();
            p.setDepartment(d);
            p.setTitle("Team Member");
            p.setLevel("mid");
            p.setActive(true);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            p.setOrganizationId(internal.getId());
            positionRepository.save(p);
        }
    }

    /** @return number of leave types inserted (skips codes that already exist for the tenant) */
    private int seedLeaveTypes(Organization internal) {
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> leaveRows = List.of(
                new Object[] {"ANNUAL", "Annual Leave", 14, true, true, 5, "Yearly paid vacation leave"},
                new Object[] {"SICK", "Sick Leave", 7, true, false, 0, "Medical or illness leave"},
                new Object[] {"PERSONAL", "Personal Leave", 3, true, false, 0, "Personal matters leave"},
                new Object[] {"MATERNITY", "Maternity Leave", 90, true, false, 0, "Paid maternity leave"},
                new Object[] {"PATERNITY", "Paternity Leave", 14, true, false, 0, "Paid paternity leave"},
                new Object[] {"UNPAID", "Unpaid Leave", 30, false, false, 0, "Unpaid extended leave"},
                new Object[] {"EMERGENCY", "Emergency Leave", 3, true, false, 0, "Immediate family emergency"});

        int added = 0;
        for (Object[] row : leaveRows) {
            String code = (String) row[0];
            if (leaveTypeRepository.findByCodeIgnoreCase(code).isPresent()) {
                continue;
            }
            LeaveType lt = new LeaveType();
            lt.setCode(code);
            lt.setName((String) row[1]);
            lt.setDaysAllowed((Integer) row[2]);
            lt.setPaid((Boolean) row[3]);
            lt.setCarryForward((Boolean) row[4]);
            lt.setMaxCarryDays((Integer) row[5]);
            lt.setDescription((String) row[6]);
            lt.setActive(true);
            lt.setCreatedAt(now);
            lt.setOrganizationId(internal.getId());
            leaveTypeRepository.save(lt);
            added++;
        }
        return added;
    }
}
