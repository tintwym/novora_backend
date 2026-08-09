package prod.tint_wym.novora_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.Organization;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;
import prod.tint_wym.novora_backend.tenancy.TenantIdResolver;

/**
 * Regression test for the bootstrap-admin path.
 *
 * <p>Background: {@code AdminUserService.ensureAdminUser} runs once at startup when
 * {@code APP_BOOTSTRAP_ADMIN_EMAIL} / {@code APP_BOOTSTRAP_ADMIN_PASSWORD} are set. The thread
 * that runs it has an empty {@link TenantContext} (no HTTP request → {@code TenantFilter} never
 * fires). It must still end up creating both:
 *
 * <ul>
 *   <li>An {@link AppUser} row whose {@code organization_id} points at the "Novora Internal"
 *       workspace (not null, not the {@link TenantIdResolver#NO_TENANT} sentinel).</li>
 *   <li>An {@link Employee} row whose {@code organization_id} matches the same workspace.</li>
 * </ul>
 *
 * <p>The bug this test pins down: an earlier version wrapped the whole method in
 * {@code @Transactional}, which made Hibernate snapshot the {@code @TenantId} resolver value
 * (NO_TENANT, because TenantContext was empty) at session-open time. The subsequent
 * {@link prod.tint_wym.novora_backend.service.MyProfileService#ensureEmployeeForEmail} call then
 * either threw "assigned tenant id differs from current tenant id" or — depending on Hibernate
 * version — silently stamped the Employee's {@code organization_id} to
 * {@code 00000000-0000-0000-0000-000000000000}, orphaning it.
 */
@SpringBootTest
class BootstrapAdminTenancyTest {

    @Autowired private AdminUserService adminUserService;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private OrganizationRepository organizationRepository;

    private static final String EMAIL = "bootstrap-admin-test@novora.internal";

    @BeforeEach
    void clearTenant() {
        // Simulate the ApplicationRunner thread: no HTTP request, so TenantFilter never set
        // anything, so this must be empty before ensureAdminUser runs — that's exactly the
        // condition under which the original bug fired.
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        // Make the test idempotent: ensureAdminUser is a no-op when the user already exists,
        // so leaving a row behind would mask a real regression on a subsequent run.
        //
        // Order matters: employees has FK -> users(id), so the employee row has to go first
        // or Postgres / H2 rejects the user delete with a referential-integrity violation.
        // We also need a tenant in context for Employee's @TenantId-scoped lookup; otherwise
        // Hibernate would query WHERE organization_id = NO_TENANT and find nothing.
        Organization internal = organizationRepository.findBySlug("novora-internal").orElse(null);
        if (internal != null) {
            TenantContext.set(internal.getId());
            try {
                employeeRepository.findByAppUser_EmailIgnoreCase(EMAIL).ifPresent(employeeRepository::delete);
            } finally {
                TenantContext.clear();
            }
        }
        appUserRepository.findByEmail(EMAIL).ifPresent(appUserRepository::delete);
        TenantContext.clear();
    }

    @Test
    void bootstrapAdminLandsInInternalWorkspaceWithMatchingEmployee() {
        Organization internal = organizationRepository.findBySlug("novora-internal").orElseThrow(
                () -> new AssertionError(
                        "Test setup expected the 'novora-internal' org to exist (data.sql / "
                                + "ReferenceDataSeeder should have created it)."));

        adminUserService.ensureAdminUser(EMAIL, "BootstrapPass1!");

        AppUser created = appUserRepository.findByEmail(EMAIL).orElseThrow(
                () -> new AssertionError("ensureAdminUser must persist an AppUser"));
        assertEquals("SUPER_ADMIN", created.getRole(), "bootstrap admin must be SUPER_ADMIN");
        assertTrue(created.isActive(), "bootstrap admin must be active");
        assertNotNull(created.getOrganizationId(), "AppUser.organization_id must not be null");
        assertNotEquals(TenantIdResolver.NO_TENANT, created.getOrganizationId(),
                "AppUser must not be stamped with the NO_TENANT sentinel");
        assertEquals(internal.getId(), created.getOrganizationId(),
                "bootstrap admin must belong to the 'novora-internal' workspace");

        // Need a tenant in context for the @TenantId-scoped Employee lookup to see the row.
        TenantContext.set(internal.getId());
        try {
            Employee employee = employeeRepository.findByAppUser_EmailIgnoreCase(EMAIL).orElseThrow(
                    () -> new AssertionError("ensureAdminUser must also persist a paired Employee"));
            UUID empOrgId = employee.getOrganizationId();
            assertNotNull(empOrgId, "Employee.organization_id must not be null");
            assertNotEquals(TenantIdResolver.NO_TENANT, empOrgId,
                    "Employee must not be stamped with the NO_TENANT sentinel — that was the original bug");
            assertEquals(internal.getId(), empOrgId,
                    "Employee.organization_id must match the AppUser's workspace");
        } finally {
            TenantContext.clear();
        }
    }
}
