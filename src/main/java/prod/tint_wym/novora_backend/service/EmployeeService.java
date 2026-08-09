package prod.tint_wym.novora_backend.service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.entity.Department;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.LeaveBalance;
import prod.tint_wym.novora_backend.entity.LeaveType;
import prod.tint_wym.novora_backend.entity.Position;
import prod.tint_wym.novora_backend.dto.HrDtos;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.repository.DepartmentRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.LeaveBalanceRepository;
import prod.tint_wym.novora_backend.repository.LeaveTypeRepository;
import prod.tint_wym.novora_backend.repository.PositionRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
public class EmployeeService {

    private static final String STATUS_TERMINATED = "terminated";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    // Tombstone shape: "deleted-<32 hex>@tombstone.local" = 56 chars (well under 255).
    // The original email is intentionally NOT preserved here — it would push long addresses past
    // the VARCHAR(255) cap and leaks PII into a search-friendly column. Audit history should live
    // in an audit table, not here.
    private static final String EMAIL_TOMBSTONE_DOMAIN = "@tombstone.local";
    private static final String EMAIL_TOMBSTONE_PREFIX = "deleted-";
    // Tombstone shape: "DEL-<12 hex>" = 16 chars (well under VARCHAR(20)).
    private static final String CODE_TOMBSTONE_PREFIX = "DEL-";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final AppUserRepository appUserRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            PositionRepository positionRepository,
            AppUserRepository appUserRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.appUserRepository = appUserRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    private Employee requireEmployee(UUID id) {
        UUID orgId = requireOrganizationId();
        Employee employee = employeeRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (STATUS_TERMINATED.equalsIgnoreCase(employee.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
        }
        return employee;
    }

    // SUPPORTS would not have created a transaction; with spring.jpa.open-in-view=false the
    // lazy navigation inside toResponse(...) (getDepartment(), getPosition(), getAppUser()) would
    // then throw LazyInitializationException. Use Spring's @Transactional(readOnly=true) so a
    // Hibernate session stays open for the duration of the call.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<HrDtos.EmployeeResponse> listEmployees() {
        // Soft-deleted rows (status='terminated') would otherwise leak into the admin roster, the
        // dashboards, and the org chart with mangled "deleted-…" emails. Exclude them by default;
        // a dedicated "terminated employees" view can be added later if HR needs the audit trail.
        return employeeRepository.findAllByStatusNotIgnoreCase(STATUS_TERMINATED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public HrDtos.EmployeeResponse getEmployee(UUID id) {
        return toResponse(requireEmployee(id));
    }

    @Transactional
    public HrDtos.EmployeeResponse createEmployee(HrDtos.CreateEmployeeRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (employeeRepository.findByAppUser_EmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee with this email already exists");
        }
        // Reject email collisions with ANY existing AppUser. Reusing the AppUser would silently
        // attach the new Employee to whoever already owns that login (potentially a former admin),
        // and the new caller has no way to set the password — so the account is also unrecoverable.
        // Admins who genuinely want to re-link should go through a dedicated "transfer ownership"
        // flow instead of POST /employees.
        if (appUserRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A user account with this email already exists. Use the user-management screen to assign roles or re-create the employee under a different email.");
        }

        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid departmentId"));

        Position position = resolvePosition(request.positionId(), department.getId());

        // New AppUser is provisioned with a random password and `active=false` so the holder cannot
        // log in until an admin activates the account via POST /api/admin/users/{id}/activate.
        LocalDateTime now = LocalDateTime.now();
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole("EMPLOYEE");
        user.setOrganizationId(orgId);
        user.setActive(false);
        user.setEmailVerified(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            user = appUserRepository.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Race with another admin creating the same email concurrently. The unique constraint
            // on users.email won; surface a 409 instead of a 500.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email is already registered", ex);
        }

        Employee employee = new Employee();
        employee.setEmail(normalizedEmail);
        employee.setAppUser(user);
        employee.setDepartment(department);
        employee.setPosition(position);
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setEmployeeCode(resolveEmployeeCode(request.employeeCode(), user.getId()));
        employee.setHireDate(request.hireDate() != null ? request.hireDate() : LocalDate.now());
        employee.setStatus("active");
        employee.setEmploymentType("full_time");
        employee.setCreatedAt(now);
        employee.setUpdatedAt(now);

        Employee saved;
        try {
            saved = employeeRepository.save(employee);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // employee_code or email unique-constraint conflict — translate to 409.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Employee record conflicts with an existing one", ex);
        }
        seedLeaveBalances(saved, now);
        return toResponse(saved);
    }

    private void seedLeaveBalances(Employee employee, LocalDateTime now) {
        int year = LocalDate.now().getYear();
        for (LeaveType leaveType : leaveTypeRepository.findAll()) {
            if (!leaveType.isActive()) {
                continue;
            }
            if (leaveBalanceRepository
                    .findByEmployee_IdAndLeaveType_IdAndBalanceYear(
                            employee.getId(), leaveType.getId(), year)
                    .isPresent()) {
                continue;
            }
            LeaveBalance balance = new LeaveBalance();
            balance.setEmployee(employee);
            balance.setLeaveType(leaveType);
            balance.setBalanceYear(year);
            BigDecimal total = BigDecimal.valueOf(leaveType.getDaysAllowed());
            balance.setTotalDays(total);
            balance.setUsedDays(BigDecimal.ZERO);
            balance.setPendingDays(BigDecimal.ZERO);
            balance.setRemainingDays(total);
            balance.setUpdatedAt(now);
            leaveBalanceRepository.save(balance);
        }
    }

    @Transactional
    public HrDtos.EmployeeResponse updateEmployee(UUID id, HrDtos.UpdateEmployeeRequest request) {
        Employee employee = requireEmployee(id);

        String normalizedEmail = request.email().trim().toLowerCase();
        employeeRepository.findByAppUser_EmailIgnoreCase(normalizedEmail).ifPresent(existing -> {
            if (!existing.getId().equals(employee.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee email already exists");
            }
        });

        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid departmentId"));
        Position position = positionRepository.findById(request.positionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid positionId"));
        if (!position.getDepartment().getId().equals(department.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Position does not belong to department");
        }

        AppUser user = employee.getAppUser();
        if (user != null && !user.getEmail().equalsIgnoreCase(normalizedEmail)) {
            // Mirror the create-path collision check so this returns 409 instead of letting the
            // unique constraint on users.email raise a 500.
            appUserRepository.findByEmail(normalizedEmail).ifPresent(other -> {
                if (!other.getId().equals(user.getId())) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "A user account with this email already exists");
                }
            });
            user.setEmail(normalizedEmail);
            user.setUpdatedAt(LocalDateTime.now());
            try {
                appUserRepository.save(user);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Email is already registered", ex);
            }
        }
        employee.setEmail(normalizedEmail);

        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setDepartment(department);
        employee.setPosition(position);
        if (request.employeeCode() != null && !request.employeeCode().isBlank()) {
            employee.setEmployeeCode(request.employeeCode().trim());
        }
        if (request.hireDate() != null) {
            employee.setHireDate(request.hireDate());
        }
        if (request.status() != null && !request.status().isBlank()) {
            String status = request.status().trim().toLowerCase();
            if (STATUS_TERMINATED.equals(status)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Use DELETE /employees/{id} to terminate an employee");
            }
            employee.setStatus(status);
        }
        employee.setUpdatedAt(LocalDateTime.now());

        Employee saved;
        try {
            saved = employeeRepository.save(employee);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Mirror createEmployee: employee_code (or a racing email) unique-constraint
            // violation should surface as 409, not an unhandled 500.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Employee record conflicts with an existing one", ex);
        }
        return toResponse(saved);
    }

    @Transactional
    public void deleteEmployee(UUID id, String callerEmail) {
        UUID orgId = requireOrganizationId();
        Employee employee = employeeRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        AppUser user = employee.getAppUser();

        // Two privilege-escalation guards that mirror AdminUserService.setUserRoles:
        //   1. Don't let admins delete themselves — they'd lose access mid-call and lock themselves
        //      out of any further admin operation.
        //   2. Don't let the last active SUPER_ADMIN be deleted — recovery would require manual DB
        //      edit + restart with APP_BOOTSTRAP_ADMIN_* set.
        if (user != null && callerEmail != null && user.getEmail() != null
                && callerEmail.trim().equalsIgnoreCase(user.getEmail().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot delete your own account. Ask another administrator.");
        }
        if (user != null && ROLE_SUPER_ADMIN.equals(user.getRole()) && user.isActive()) {
            // Per-workspace guard: a workspace must always have at least one active admin.
            // Scope to the deleted user's organization rather than the global pool, otherwise
            // an Acme admin would be blocked from deleting their own last admin just because
            // Novora Internal still has one (or vice-versa).
            UUID userOrgId = user.getOrganizationId();
            List<AppUser> peers = userOrgId == null
                    ? appUserRepository.findAll()
                    : appUserRepository.findAllByOrganizationId(userOrgId);
            long remainingSuperAdmins = peers.stream()
                    .filter(u -> !u.getId().equals(user.getId()))
                    .filter(u -> ROLE_SUPER_ADMIN.equals(u.getRole()))
                    .filter(u -> u.isActive())
                    .count();
            if (remainingSuperAdmins == 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot delete the last active SUPER_ADMIN. Promote another user first.");
            }
        }

        // Soft delete: a hard delete throws DataIntegrityViolationException whenever the employee
        // has any related row (leave requests, attendance, payroll, documents, ...). All those FKs
        // are NOT NULL with no ON DELETE CASCADE, so a real delete cascade would either require
        // schema changes or a fragile multi-table cleanup. Tombstoning preserves the audit trail
        // and is what HR systems usually want anyway. The linked AppUser is deactivated so the
        // person can no longer sign in.
        //
        // We also rename the email and employee_code columns to free the original values for
        // re-use. Without this, the unique constraints on employees.email and users.email would
        // permanently lock those addresses out, and there is no "re-hire" code path to release
        // them. The tombstone shape is fixed-width and bounded so it always fits the column caps
        // (employee_code is VARCHAR(20) and email is VARCHAR(255) — the original code with a
        // "DEL-<8hex>-" prefix overflowed VARCHAR(20) for every auto-generated 10-char code).
        LocalDateTime now = LocalDateTime.now();
        String emailTombstone = buildEmailTombstone(employee.getId());
        String codeTombstone = buildCodeTombstone(employee.getId());

        employee.setStatus(STATUS_TERMINATED);
        if (employee.getEmail() != null && !employee.getEmail().startsWith(EMAIL_TOMBSTONE_PREFIX)) {
            employee.setEmail(emailTombstone);
        }
        if (employee.getEmployeeCode() != null && !employee.getEmployeeCode().startsWith(CODE_TOMBSTONE_PREFIX)) {
            employee.setEmployeeCode(codeTombstone);
        }
        employee.setEndDate(LocalDate.now());
        employee.setUpdatedAt(now);
        employeeRepository.save(employee);

        if (user != null) {
            if (user.isActive()) {
                user.setActive(false);
            }
            if (user.getEmail() != null && !user.getEmail().startsWith(EMAIL_TOMBSTONE_PREFIX)) {
                user.setEmail(emailTombstone);
            }
            user.setUpdatedAt(now);
            appUserRepository.save(user);
        }
    }

    private Position resolvePosition(UUID positionId, UUID departmentId) {
        if (positionId != null) {
            return positionRepository.findById(positionId)
                    .filter(p -> p.getDepartment().getId().equals(departmentId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid positionId"));
        }
        return positionRepository.findFirstByDepartment_IdOrderByTitleAsc(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No position for department"));
    }

    private static String resolveEmployeeCode(String requested, UUID userId) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        return "E-" + userId.toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /**
     * Deterministic tombstone for {@code employees.email} and {@code users.email} during soft-delete.
     * Shape: {@code deleted-<32-hex employee id>@tombstone.local} = 8 + 32 + 16 = <strong>56 chars</strong>,
     * always within the {@code VARCHAR(255)} cap on both columns. Idempotent — same employee
     * deleted twice produces the same tombstone, so the {@code !startsWith(prefix)} guard works.
     */
    static String buildEmailTombstone(UUID employeeId) {
        String fullId = employeeId.toString().replace("-", "");
        return EMAIL_TOMBSTONE_PREFIX + fullId + EMAIL_TOMBSTONE_DOMAIN;
    }

    /**
     * Deterministic tombstone for {@code employees.employee_code} during soft-delete. Shape:
     * {@code DEL-<12-hex prefix>} = 16 chars, always within {@code VARCHAR(20)}. The previous
     * tombstone of {@code "DEL-<8 hex>-<originalCode>"} silently overflowed for every
     * auto-generated 10-char employee code (resulting length 23) → 500 on every delete.
     */
    static String buildCodeTombstone(UUID employeeId) {
        return CODE_TOMBSTONE_PREFIX + employeeId.toString().replace("-", "").substring(0, 12);
    }

    private HrDtos.EmployeeResponse toResponse(Employee employee) {
        Department dept = employee.getDepartment();
        UUID deptId = dept != null ? dept.getId() : null;
        String deptName = dept != null ? dept.getName() : null;
        Position pos = employee.getPosition();
        UUID posId = pos != null ? pos.getId() : null;
        String posTitle = pos != null ? pos.getTitle() : null;

        Optional<AppUser> account = Optional.ofNullable(employee.getAppUser());
        UUID userId = account.map(u -> u.getId()).orElse(null);
        List<String> accountRoles = account
                .map(u -> List.of(u.getRole() == null ? "EMPLOYEE" : u.getRole()))
                .orElse(List.of());

        String email = employee.getEmail();
        if (email == null || email.isBlank()) {
            email = account.map(u -> u.getEmail()).orElse("");
        }

        String managerName = null;
        Employee manager = employee.getManager();
        if (manager != null) {
            managerName = formatPersonDisplayName(manager.getFirstName(), manager.getLastName());
        }

        return new HrDtos.EmployeeResponse(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                email,
                deptId,
                deptName,
                posId,
                posTitle,
                userId,
                accountRoles,
                employee.getEmployeeCode(),
                employee.getHireDate(),
                employee.getStatus(),
                employee.getPhone(),
                employee.getEmploymentType(),
                employee.getCity(),
                employee.getCountry(),
                managerName
        );
    }

    private static String formatPersonDisplayName(String firstName, String lastName) {
        String f = firstName == null ? "" : firstName.trim();
        String l = lastName == null ? "" : lastName.trim();
        if (f.isEmpty()) {
            return l;
        }
        if (l.isEmpty()) {
            return f;
        }
        return f + " " + l;
    }
}
