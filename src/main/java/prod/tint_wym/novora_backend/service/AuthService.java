package prod.tint_wym.novora_backend.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import prod.tint_wym.novora_backend.config.AppUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.entity.Department;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.LeaveType;
import prod.tint_wym.novora_backend.entity.Organization;
import prod.tint_wym.novora_backend.entity.Position;
import prod.tint_wym.novora_backend.dto.AuthDtos;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.repository.DepartmentRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.LeaveTypeRepository;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;
import prod.tint_wym.novora_backend.repository.PositionRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;
import prod.tint_wym.novora_backend.firebase.FirebaseAuthenticationToken;

@Service
public class AuthService {

    /** Trial duration for every demo signup. Changing this to e.g. {@code 14} flips it system-wide. */
    private static final int TRIAL_DAYS = 30;

    private final AppUserRepository appUserRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService userDetailsService;
    private final TransactionTemplate transactionTemplate;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthService(
            AppUserRepository appUserRepository,
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            PositionRepository positionRepository,
            LeaveTypeRepository leaveTypeRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            AppUserDetailsService userDetailsService,
            PlatformTransactionManager transactionManager
    ) {
        this.appUserRepository = appUserRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Public signup. New accounts are always provisioned as {@code EMPLOYEE} with a pending
     * job position — Admin (or HR Admin) assigns the real position/role after reviewing
     * performance. Workspace Admin itself is bootstrapped via {@code APP_BOOTSTRAP_ADMIN_*}
     * env vars, never via public register.
     *
     * <p>When an Admin already exists, the registrant joins that Admin's organization so they
     * can be managed. Otherwise a new trial workspace is created from {@code companyName}.
     *
     * <p>This is intentionally <strong>not</strong> wrapped in a single {@code @Transactional}
     * boundary. Hibernate's {@code @TenantId} resolver is queried at session-open time and the
     * answer is cached for the entire transaction; if we opened a transaction before pushing the
     * org id into {@link TenantContext}, every tenant-scoped save would fail with
     * "assigned tenant id differs from current tenant id".
     */
    public AuthDtos.AuthResponse register(
            AuthDtos.RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (appUserRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        ResolvedOrg resolved = resolveOrganizationForSignup(request.companyName());
        Organization org = resolved.org();
        boolean createdNewOrg = resolved.createdNew();

        UUID previousTenant = TenantContext.get();
        TenantContext.set(org.getId());
        AppUser saved;
        try {
            saved = transactionTemplate.execute(status -> {
                ensureDefaultDepartmentAndPendingPosition(org);
                seedDefaultLeaveTypes(org);
                AppUser user = createEmployeeUserWithPassword(normalizedEmail, request.password(), org);
                ensureEmployeeForUser(user, normalizedEmail, request.fullName(), org);
                return user;
            });
        } catch (DataIntegrityViolationException ex) {
            TenantContext.clear();
            deleteOrphanOrgIfNeeded(org, createdNewOrg);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered", ex);
        } catch (RuntimeException ex) {
            TenantContext.clear();
            deleteOrphanOrgIfNeeded(org, createdNewOrg);
            throw ex;
        } finally {
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previousTenant);
            }
        }

        authenticateAndCreateSession(normalizedEmail, request.password(), httpRequest, httpResponse);

        return toAuthResponse(saved, org);
    }

    /**
     * Provisions an {@link AppUser} for a Firebase-authenticated caller who does not yet have a
     * row. Same policy as password register: role is {@code EMPLOYEE}, pending position, join
     * Admin's workspace when one exists.
     */
    public AuthDtos.AuthResponse registerWithFirebase(
            AuthDtos.FirebaseRegisterRequest request, Authentication authentication) {
        if (!(authentication instanceof FirebaseAuthenticationToken firebase)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firebase authentication required");
        }
        if (firebase.isProvisioned()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workspace already provisioned");
        }
        String email = firebase.getEmail();
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Firebase account must have a verified email");
        }
        final String normalizedEmail = email.trim().toLowerCase();
        if (appUserRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        final String firebaseUid = firebase.getFirebaseUid();

        ResolvedOrg resolved = resolveOrganizationForSignup(request.companyName());
        Organization org = resolved.org();
        boolean createdNewOrg = resolved.createdNew();

        UUID previousTenant = TenantContext.get();
        TenantContext.set(org.getId());
        AppUser saved;
        try {
            saved = transactionTemplate.execute(status -> {
                ensureDefaultDepartmentAndPendingPosition(org);
                seedDefaultLeaveTypes(org);
                AppUser user = createFirebaseEmployeeUser(normalizedEmail, firebaseUid, org);
                ensureEmployeeForUser(user, normalizedEmail, request.fullName(), org);
                return user;
            });
        } catch (RuntimeException ex) {
            TenantContext.clear();
            deleteOrphanOrgIfNeeded(org, createdNewOrg);
            throw ex;
        } finally {
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previousTenant);
            }
        }

        return toAuthResponse(saved, org);
    }

    private record ResolvedOrg(Organization org, boolean createdNew) {
    }

    /**
     * Prefer the workspace that already has an Admin so self-registered employees land where
     * position/role can be assigned. Fall back to creating a new trial org from company name.
     */
    private ResolvedOrg resolveOrganizationForSignup(String companyName) {
        Organization adminOrg = findOrganizationManagedByAdmin();
        if (adminOrg != null) {
            return new ResolvedOrg(adminOrg, false);
        }
        try {
            Organization created = transactionTemplate.execute(status -> createTrialOrganization(companyName));
            return new ResolvedOrg(created, true);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Could not create workspace (name may already be taken)", ex);
        }
    }

    private Organization findOrganizationManagedByAdmin() {
        return appUserRepository.findAll().stream()
                .filter(u -> u.isActive() && "SUPER_ADMIN".equals(u.getRole()) && u.getOrganizationId() != null)
                .map(u -> organizationRepository.findById(u.getOrganizationId()).orElse(null))
                .filter(org -> org != null)
                .findFirst()
                .orElse(null);
    }

    private void deleteOrphanOrgIfNeeded(Organization org, boolean createdNewOrg) {
        if (!createdNewOrg) {
            return;
        }
        try {
            organizationRepository.deleteById(org.getId());
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public AuthDtos.AuthResponse login(
            AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String normalizedEmail = request.email().trim().toLowerCase();
        authenticateAndCreateSession(normalizedEmail, request.password(), httpRequest, httpResponse);

        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        Organization org = loadOrganization(user.getOrganizationId());
        return toAuthResponse(user, org);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public AuthDtos.AuthResponse me(
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        if (authentication instanceof FirebaseAuthenticationToken firebase && !firebase.isProvisioned()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Workspace not provisioned — complete registration with your company name");
        }

        String email = authentication.getName().trim().toLowerCase();
        // Tear the session down whenever the SecurityContext refers to a user the DB no longer
        // knows under that email. The previous order ran `orElseThrow` first, which meant a
        // soft-deleted user (whose email was mangled to `deleted-…+…` by EmployeeService) kept
        // their cached SecurityContext alive until the session TTL — they couldn't load `/me` but
        // they also weren't actively booted off either. Invalidate first, then throw 401.
        var maybeUser = appUserRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            invalidateSession(httpRequest);
            SecurityContextHolder.clearContext();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
        }
        AppUser user = maybeUser.get();
        // Admin deactivated the user account directly — same treatment.
        if (!user.isActive()) {
            invalidateSession(httpRequest);
            SecurityContextHolder.clearContext();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }
        refreshSessionRoles(email, authentication, httpRequest, httpResponse);
        Organization org = loadOrganization(user.getOrganizationId());
        return toAuthResponse(user, org);
    }

    private Organization createTrialOrganization(String companyName) {
        String name = companyName.trim();
        String slug = uniqueSlug(name);
        LocalDateTime now = LocalDateTime.now();

        Organization org = new Organization();
        org.setName(name);
        org.setSlug(slug);
        org.setPlan(Organization.Plan.TRIAL);
        org.setStatus(Organization.Status.ACTIVE);
        org.setTrialStartedAt(now);
        org.setTrialExpiresAt(now.plusDays(TRIAL_DAYS));
        org.setCreatedAt(now);
        org.setUpdatedAt(now);
        return organizationRepository.save(org);
    }

    private static final String PENDING_POSITION_TITLE = "Pending Assignment";

    private void ensureDefaultDepartmentAndPendingPosition(Organization org) {
        LocalDateTime now = LocalDateTime.now();
        Department dept = departmentRepository.findAll().stream().findFirst().orElse(null);
        if (dept == null) {
            // Org-prefixed code so two trial orgs naming a department "GEN" don't collide on the
            // legacy global UNIQUE constraint on departments(code). A V12 migration will replace
            // that with UNIQUE(organization_id, code), at which point this prefix can be dropped.
            String orgIdPrefix = org.getId().toString().replace("-", "").substring(0, 8).toUpperCase();
            dept = new Department();
            dept.setName("General");
            dept.setCode("GEN-" + orgIdPrefix);
            dept.setDescription("Default department created on signup");
            dept.setActive(true);
            dept.setCreatedAt(now);
            dept.setUpdatedAt(now);
            dept.setOrganizationId(org.getId());
            dept = departmentRepository.save(dept);
        }

        boolean hasPending = positionRepository.findByDepartment_Id(dept.getId()).stream()
                .anyMatch(p -> PENDING_POSITION_TITLE.equalsIgnoreCase(p.getTitle()));
        if (!hasPending) {
            Position position = new Position();
            position.setTitle(PENDING_POSITION_TITLE);
            position.setDepartment(dept);
            position.setLevel("entry");
            position.setActive(true);
            position.setCreatedAt(now);
            position.setUpdatedAt(now);
            position.setOrganizationId(org.getId());
            positionRepository.save(position);
        }
    }

    private void seedDefaultLeaveTypes(Organization org) {
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> leaveRows = List.of(
                new Object[] {"ANNUAL", "Annual Leave", 14, true, true, 5, "Yearly paid vacation leave"},
                new Object[] {"SICK", "Sick Leave", 7, true, false, 0, "Medical or illness leave"},
                new Object[] {"PERSONAL", "Personal Leave", 3, true, false, 0, "Personal matters leave"},
                new Object[] {"UNPAID", "Unpaid Leave", 30, false, false, 0, "Unpaid extended leave"});

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
            lt.setOrganizationId(org.getId());
            leaveTypeRepository.save(lt);
        }
    }

    private AppUser createEmployeeUserWithPassword(
            String normalizedEmail, String password, Organization org) {
        return persistEmployeeUser(normalizedEmail, null, org, password);
    }

    private AppUser createFirebaseEmployeeUser(
            String normalizedEmail, String firebaseUid, Organization org) {
        // Firebase-only accounts never authenticate with this hash — random placeholder satisfies NOT NULL.
        String placeholder = UUID.randomUUID() + "Aa1!";
        return persistEmployeeUser(normalizedEmail, firebaseUid, org, placeholder);
    }

    private AppUser persistEmployeeUser(
            String normalizedEmail, String firebaseUid, Organization org, String rawPassword) {
        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirebaseUid(firebaseUid);
        user.setOrganizationId(org.getId());
        // Public signup never grants admin. Admin assigns role/position after performance review.
        user.setRole("EMPLOYEE");
        user.setActive(true);
        user.setEmailVerified(firebaseUid != null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return appUserRepository.save(user);
    }

    private static void invalidateSession(HttpServletRequest httpRequest) {
        var session = httpRequest.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                // Already torn down by another concurrent request — nothing to do.
            }
        }
    }

    /** Reload ROLE_* authorities from DB so a role change in Neon applies without re-login. */
    private void refreshSessionRoles(
            String email,
            Authentication previous,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        UserDetails details = userDetailsService.loadUserByUsername(email);
        var refreshed = new UsernamePasswordAuthenticationToken(
                details, details.getPassword(), details.getAuthorities());
        if (previous != null) {
            refreshed.setDetails(previous.getDetails());
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(refreshed);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }

    private void authenticateAndCreateSession(
            String email,
            String password,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        var auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        // Session fixation protection: rotate JSESSIONID on every successful authentication so any
        // pre-existing session ID an attacker may have planted on the victim's browser is invalidated.
        // changeSessionId() preserves attributes (none today, but cheap insurance) and is the default
        // strategy Spring Security uses when authentication flows through its filters.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        } else {
            httpRequest.getSession(true);
        }
        var session = httpRequest.getSession(false);
        if (session != null) {
            // Match server.servlet.session.timeout=1h (3600 seconds).
            session.setMaxInactiveInterval(3600);
        }
        SecurityContextHolder.getContext().setAuthentication(auth);
        securityContextRepository.saveContext(SecurityContextHolder.getContext(), httpRequest, httpResponse);
    }

    /**
     * Loads the caller's org. Returns null only when the column is unset (legacy data backfill
     * still pending) — caller must tolerate a null snapshot.
     */
    private Organization loadOrganization(UUID organizationId) {
        if (organizationId == null) return null;
        return organizationRepository.findById(organizationId).orElse(null);
    }

    private AuthDtos.AuthResponse toAuthResponse(AppUser user, Organization org) {
        String role = user.getRole() == null ? "EMPLOYEE" : user.getRole();
        AuthDtos.OrganizationSnapshot snapshot = org == null ? null : new AuthDtos.OrganizationSnapshot(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getPlan().name(),
                org.getStatus().name(),
                org.getTrialExpiresAt());
        String fullName = resolveDisplayName(user.getEmail());
        return new AuthDtos.AuthResponse(
                null,
                "Session",
                user.getId(),
                user.getEmail(),
                fullName,
                List.of(role),
                snapshot);
    }

    private String resolveDisplayName(String email) {
        if (email == null || email.isBlank()) return null;
        return employeeRepository.findByAppUser_EmailIgnoreCase(email.trim().toLowerCase())
                .map(e -> formatPersonDisplayName(e.getFirstName(), e.getLastName()))
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);
    }

    private static String formatPersonDisplayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? null : combined;
    }

    private void ensureEmployeeForUser(AppUser user, String normalizedEmail, String fullName, Organization org) {
        if (employeeRepository.findByAppUser_EmailIgnoreCase(normalizedEmail).isPresent()) {
            return;
        }
        // Pull the seeded department/position from THIS tenant only — Hibernate scopes findAll
        // automatically because Department and Position are @TenantId entities.
        Department department = departmentRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No department seed for org"));
        // Prefer the pending-assignment position so Admin can later promote after performance review.
        Position position = positionRepository.findByDepartment_Id(department.getId()).stream()
                .filter(p -> PENDING_POSITION_TITLE.equalsIgnoreCase(p.getTitle()))
                .findFirst()
                .or(() -> positionRepository.findFirstByDepartment_IdOrderByTitleAsc(department.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No position seed for org"));

        LocalDateTime now = LocalDateTime.now();
        Employee e = new Employee();
        e.setOrganizationId(org.getId());
        e.setEmail(normalizedEmail);
        e.setAppUser(user);
        e.setDepartment(department);
        e.setPosition(position);

        // Prefer the explicit Full Name from the signup form; fall back to splitting the email
        // local-part on common separators only when the client didn't send a name (older builds
        // or API consumers).
        String[] nameParts = splitFullName(fullName);
        String first = nameParts[0];
        String last = nameParts[1];
        if (first.isEmpty() || last.isEmpty()) {
            String local = normalizedEmail.split("@", 2)[0];
            String[] localParts = local.split("[._\\-+]+");
            if (first.isEmpty()) {
                first = localParts.length > 0 && !localParts[0].isBlank() ? cap(localParts[0]) : "User";
            }
            if (last.isEmpty()) {
                last = localParts.length > 1 && !localParts[1].isBlank() ? cap(localParts[1]) : "";
            }
        }
        e.setFirstName(first);
        e.setLastName(last);
        e.setEmployeeCode("E-" + user.getId().toString().replace("-", "").substring(0, 8).toUpperCase());
        e.setHireDate(LocalDate.now());
        e.setStatus("active");
        e.setEmploymentType("full_time");
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        employeeRepository.save(e);
    }

    /** Returns {first, last}. Either entry may be empty when the input is null/blank. */
    private static String[] splitFullName(String fullName) {
        if (fullName == null) return new String[]{"", ""};
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) return new String[]{"", ""};
        // Collapse runs of whitespace so "John   Doe" → ["John", "Doe"], not ["John", "", "Doe"].
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 1) {
            return new String[]{cap(tokens[0]), ""};
        }
        String firstName = cap(tokens[0]);
        // Everything after the first token becomes the last name, preserving spaces between parts.
        StringBuilder lastBuilder = new StringBuilder();
        for (int i = 1; i < tokens.length; i++) {
            if (i > 1) lastBuilder.append(' ');
            lastBuilder.append(cap(tokens[i]));
        }
        return new String[]{firstName, lastBuilder.toString()};
    }

    private static String cap(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isBlank()) return "User";
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }

    /**
     * Generates a URL-safe slug from the company name and ensures it's unique by appending a
     * short hex suffix when there's a collision. Slugs are at most 80 chars to fit the DB column.
     */
    private String uniqueSlug(String companyName) {
        String base = baseSlug(companyName);
        if (base.isEmpty()) base = "workspace";
        if (!organizationRepository.existsBySlug(base)) {
            return base;
        }
        // Up to 8 attempts with random suffixes — for the same base name a collision is rare.
        for (int attempt = 0; attempt < 8; attempt++) {
            String suffix = "-" + UUID.randomUUID().toString().substring(0, 6);
            int trimTo = Math.min(base.length(), 80 - suffix.length());
            String candidate = base.substring(0, trimTo) + suffix;
            if (!organizationRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        // Astronomically unlikely. If it does happen, give up cleanly so we don't loop forever.
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Could not allocate a unique workspace slug");
    }

    private static String baseSlug(String name) {
        String normalized = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String lower = normalized.toLowerCase(Locale.ROOT);
        String hyphenated = lower.replaceAll("[^a-z0-9]+", "-");
        String trimmed = hyphenated.replaceAll("^-+|-+$", "");
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

    /** Test seam: surface the trial duration without exposing the constant. */
    public static int trialDays() {
        return TRIAL_DAYS;
    }
}
