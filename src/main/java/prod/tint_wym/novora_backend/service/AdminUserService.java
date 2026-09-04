package prod.tint_wym.novora_backend.service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.entity.Organization;
import prod.tint_wym.novora_backend.dto.AdminDtos;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
public class AdminUserService {

    private static final Set<String> ALLOWED_ROLES =
            Set.of("SUPER_ADMIN", "HR_ADMIN", "HR_MANAGER", "MANAGER", "EMPLOYEE");
    private static final String INTERNAL_ORG_SLUG = "novora-internal";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final MyProfileService myProfileService;
    private final OrganizationRepository organizationRepository;
    private final TransactionTemplate transactionTemplate;

    public AdminUserService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            MyProfileService myProfileService,
            OrganizationRepository organizationRepository,
            PlatformTransactionManager transactionManager) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.myProfileService = myProfileService;
        this.organizationRepository = organizationRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // AppUser has no lazy associations today, so SUPPORTS would be safe — but a real read-only
    // tx is consistent with the rest of the read services and future-proof if we ever add lazy
    // relations (e.g. roles as a Set).
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<AdminDtos.UserSummary> listUsers() {
        // Admins see only their own workspace's users. AppUser is intentionally not @TenantId
        // (so login can find users globally), so we filter explicitly here.
        UUID tenant = TenantContext.get();
        if (tenant == null) {
            // Defensive: unauthenticated callers should never reach this method (the controller
            // is behind an auth guard), but if it ever does happen, return nothing rather than
            // dumping the global user list.
            return List.of();
        }
        return appUserRepository.findAllByOrganizationId(tenant).stream()
                .map(AdminUserService::toSummary)
                .toList();
    }

    @Transactional
    public AdminDtos.UserSummary setUserRoles(
            UUID id, AdminDtos.UpdateUserRolesRequest request, String callerEmail) {
        UUID tenant = TenantContext.get();
        if (tenant == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        // Use the org-scoped lookup so an admin in one workspace can never modify a user in
        // another, even with a guessable user UUID.
        AppUser user = appUserRepository.findByIdAndOrganizationId(id, tenant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<String> normalized = request.roles().stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isBlank())
                .map(s -> s.toUpperCase())
                .distinct()
                .sorted()
                .toList();

        if (normalized.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exactly one role is required");
        }

        String roleName = normalized.get(0);
        if (!ALLOWED_ROLES.contains(roleName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + roleName);
        }

        String currentRole = user.getRole() == null ? "EMPLOYEE" : user.getRole();
        boolean isDemotionFromSuperAdmin = "SUPER_ADMIN".equals(currentRole) && !"SUPER_ADMIN".equals(roleName);

        // Only a SUPER_ADMIN may grant the SUPER_ADMIN role or change an existing
        // SUPER_ADMIN's role. Without this, an HR_ADMIN (who can reach this endpoint)
        // could promote an account to SUPER_ADMIN — a privilege escalation above their own.
        String callerRole = callerEmail == null
                ? null
                : appUserRepository.findByEmail(callerEmail.trim().toLowerCase())
                        .map(caller -> caller.getRole() == null ? "EMPLOYEE" : caller.getRole())
                        .orElse(null);
        boolean callerIsSuperAdmin = "SUPER_ADMIN".equals(callerRole);
        if (!callerIsSuperAdmin && ("SUPER_ADMIN".equals(roleName) || "SUPER_ADMIN".equals(currentRole))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only a SUPER_ADMIN can assign or modify the SUPER_ADMIN role.");
        }
        boolean targetIsCaller = callerEmail != null && user.getEmail() != null
                && callerEmail.trim().equalsIgnoreCase(user.getEmail().trim());

        // Don't let an admin demote themselves: the caller would lose access to this endpoint
        // mid-call and could lock themselves out by accident. Self-promotion to a higher role
        // would also be a privilege-escalation vector.
        if (targetIsCaller && !roleName.equals(currentRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You cannot change your own role. Ask another administrator.");
        }

        // Don't allow demoting the last SUPER_ADMIN of *this* workspace — that would leave the
        // workspace with no one capable of managing roles, and the only recovery would be a
        // manual DB edit. Per-tenant: a workspace must always have at least one active admin.
        if (isDemotionFromSuperAdmin) {
            long remainingSuperAdmins = appUserRepository.findAllByOrganizationId(tenant).stream()
                    .filter(u -> !u.getId().equals(user.getId()))
                    .filter(u -> "SUPER_ADMIN".equals(u.getRole()))
                    .filter(u -> u.isActive())
                    .count();
            if (remainingSuperAdmins == 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot demote the last active SUPER_ADMIN. Promote another user first.");
            }
        }

        user.setRole(roleName);
        user.setUpdatedAt(LocalDateTime.now());
        AppUser saved = appUserRepository.save(user);
        return toSummary(saved);
    }

    /**
     * Activates a provisioned login (created inactive with EmployeeService) by setting a known
     * password. Without this path, new employees can never sign in.
     */
    @Transactional
    public AdminDtos.ActivateUserResponse activateUser(
            UUID id, AdminDtos.ActivateUserRequest request, String callerEmail) {
        UUID tenant = TenantContext.get();
        if (tenant == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        AppUser user = appUserRepository.findByIdAndOrganizationId(id, tenant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Complexity already enforced by ActivateUserRequest @Pattern (same as register).
        String password = request.password();

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);
        user.setPasswordResetToken(null);
        user.setPasswordResetExp(null);
        user.setUpdatedAt(LocalDateTime.now());
        AppUser saved = appUserRepository.save(user);
        return new AdminDtos.ActivateUserResponse(saved.getId(), saved.getEmail(), saved.isActive());
    }

    @Transactional
    public AdminDtos.UserSummary inviteUser(AdminDtos.InviteUserRequest request, String callerEmail) {
        UUID tenant = TenantContext.get();
        if (tenant == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String normalizedEmail = request.email().trim().toLowerCase();
        if (appUserRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String roleName = "EMPLOYEE";
        if (request.roles() != null && !request.roles().isEmpty()) {
            List<String> normalized = request.roles().stream()
                    .map(s -> s == null ? "" : s.trim())
                    .filter(s -> !s.isBlank())
                    .map(s -> s.toUpperCase())
                    .distinct()
                    .toList();
            if (normalized.size() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exactly one role is required");
            }
            roleName = normalized.get(0);
            if (!ALLOWED_ROLES.contains(roleName)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + roleName);
            }
            String callerRole = callerEmail == null
                    ? null
                    : appUserRepository.findByEmail(callerEmail.trim().toLowerCase())
                            .map(caller -> caller.getRole() == null ? "EMPLOYEE" : caller.getRole())
                            .orElse(null);
            if (!"SUPER_ADMIN".equals(callerRole) && "SUPER_ADMIN".equals(roleName)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Only a SUPER_ADMIN can assign the SUPER_ADMIN role.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.temporaryPassword()));
        user.setOrganizationId(tenant);
        user.setRole(roleName);
        // Invited with a known temp password — active so they can sign in immediately.
        user.setActive(true);
        user.setEmailVerified(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        AppUser saved = appUserRepository.save(user);
        myProfileService.ensureEmployeeForEmail(normalizedEmail);
        return toSummary(saved);
    }

    @Transactional
    public AdminDtos.UserSummary deactivateUser(UUID id, String callerEmail) {
        UUID tenant = TenantContext.get();
        if (tenant == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        AppUser user = appUserRepository.findByIdAndOrganizationId(id, tenant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (callerEmail != null
                && user.getEmail() != null
                && callerEmail.trim().equalsIgnoreCase(user.getEmail().trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot deactivate your own account");
        }

        if ("SUPER_ADMIN".equals(user.getRole()) && user.isActive()) {
            long remainingSuperAdmins = appUserRepository.findAllByOrganizationId(tenant).stream()
                    .filter(u -> !u.getId().equals(user.getId()))
                    .filter(u -> "SUPER_ADMIN".equals(u.getRole()))
                    .filter(u -> u.isActive())
                    .count();
            if (remainingSuperAdmins == 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot deactivate the last active SUPER_ADMIN. Promote another user first.");
            }
        }

        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        return toSummary(appUserRepository.save(user));
    }

    public void ensureAdminUser(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        if (appUserRepository.findByEmail(normalizedEmail).isPresent()) {
            return;
        }

        // Bootstrap admin always belongs to the Novora Internal workspace. Without this,
        // the AppUser's organization_id would be null, the request-time tenant resolver
        // would report no tenant, and Hibernate would skip the @TenantId predicate on every
        // query — letting the bootstrap admin's GETs pull rows from demo customer workspaces.
        Optional<Organization> internal = organizationRepository.findBySlug(INTERNAL_ORG_SLUG);
        UUID internalOrgId = internal.map(org -> org.getId()).orElse(null);

        // Phase 1: create the AppUser. AppUser is intentionally not @TenantId-scoped, so we can
        // (and must) do this without a tenant in context. We deliberately don't wrap the whole
        // method in one big @Transactional: Hibernate snapshots the @TenantId resolver value at
        // session-open time and caches it for the whole transaction. If we opened the tx here
        // (with TenantContext empty) and then set TenantContext below, the Employee insert inside
        // ensureEmployeeForEmail would either fail with "assigned tenant id differs from current
        // tenant id" or — worse — silently stamp organization_id = 00000000... (the NO_TENANT
        // sentinel), orphaning the row. Splitting into two transactions makes the tenant set in
        // step 2 actually take effect.
        transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            AppUser user = new AppUser();
            user.setEmail(normalizedEmail);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setOrganizationId(internalOrgId);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            user.setRole("SUPER_ADMIN");
            user.setActive(true);
            user.setEmailVerified(true);
            return appUserRepository.save(user);
        });

        if (internalOrgId == null) {
            // ReferenceDataSeeder (which seeds the Internal org) is @Order(0) and so runs first,
            // but if a future refactor breaks that ordering we want a clear failure rather than
            // a half-bootstrapped admin with no Employee record. The AppUser is already committed
            // so the operator can recover by restarting once the seeder catches up.
            throw new IllegalStateException(
                    "Cannot bootstrap admin: '" + INTERNAL_ORG_SLUG + "' organization missing. "
                            + "Verify ReferenceDataSeeder runs before BootstrapAdminConfiguration.");
        }

        // Phase 2: provision the Employee row inside a fresh transaction that opens *after*
        // TenantContext has been pushed, so Hibernate's session-cached tenant matches the
        // organization_id @TenantId will stamp on the new row.
        UUID priorTenant = TenantContext.get();
        TenantContext.set(internalOrgId);
        try {
            transactionTemplate.execute(status -> {
                myProfileService.ensureEmployeeForEmail(normalizedEmail);
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

    private static AdminDtos.UserSummary toSummary(AppUser user) {
        String role = user.getRole() == null ? "EMPLOYEE" : user.getRole();
        return new AdminDtos.UserSummary(user.getId(), user.getEmail(), List.of(role), user.isActive());
    }
}
