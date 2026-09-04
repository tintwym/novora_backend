package prod.tint_wym.novora_backend.service;

import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.entity.Department;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.EmployeeEducation;
import prod.tint_wym.novora_backend.entity.EmployeeFamily;
import prod.tint_wym.novora_backend.entity.Position;
import prod.tint_wym.novora_backend.dto.MyProfileDtos;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.repository.DepartmentRepository;
import prod.tint_wym.novora_backend.repository.EmployeeEducationRepository;
import prod.tint_wym.novora_backend.repository.EmployeeFamilyRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.PositionRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
public class MyProfileService {

    public static final String OTP_PURPOSE_PERSONAL = "PERSONAL_PROFILE";

    private final EmployeeRepository employeeRepository;
    private final AppUserRepository appUserRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final ProfileOtpService profileOtpService;
    private final EmployeeFamilyRepository employeeFamilyRepository;
    private final EmployeeEducationRepository employeeEducationRepository;
    private final MyProfileService self;

    public MyProfileService(
            EmployeeRepository employeeRepository,
            AppUserRepository appUserRepository,
            DepartmentRepository departmentRepository,
            PositionRepository positionRepository,
            ProfileOtpService profileOtpService,
            EmployeeFamilyRepository employeeFamilyRepository,
            EmployeeEducationRepository employeeEducationRepository,
            @Lazy MyProfileService self
    ) {
        this.employeeRepository = employeeRepository;
        this.appUserRepository = appUserRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.profileOtpService = profileOtpService;
        this.employeeFamilyRepository = employeeFamilyRepository;
        this.employeeEducationRepository = employeeEducationRepository;
        this.self = self;
    }

    @Transactional
    public Employee ensureEmployeeForEmail(String email) {
        String normalized = normalizeEmail(email);
        AppUser user = appUserRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        var existing = employeeRepository.findByAppUser_EmailIgnoreCase(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }
        Department department = departmentRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No department seed"));
        Position position = positionRepository.findFirstByDepartment_IdOrderByTitleAsc(department.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No position seed"));
        LocalDateTime now = LocalDateTime.now();
        Employee e = new Employee();
        e.setEmail(normalized);
        e.setAppUser(user);
        e.setDepartment(department);
        e.setPosition(position);
        String[] parts = normalized.split("@", 2)[0].split("[._\\-+]+");
        String first = parts.length > 0 && !parts[0].isBlank() ? cap(parts[0]) : "User";
        String last = parts.length > 1 && !parts[1].isBlank() ? cap(parts[1]) : "Employee";
        e.setFirstName(first);
        e.setLastName(last);
        e.setEmployeeCode("E-" + user.getId().toString().replace("-", "").substring(0, 8).toUpperCase());
        e.setHireDate(LocalDate.now());
        e.setStatus("active");
        e.setEmploymentType("full_time");
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        try {
            return employeeRepository.save(e);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent first-login requests for the same email both passed the existence check
            // above; one save wins via the unique constraint on (app_user_id) / (email). Re-read the
            // row the winner inserted instead of bubbling a 500 up to the loser.
            return employeeRepository.findByAppUser_EmailIgnoreCase(normalized)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "Employee record conflicted while provisioning", ex));
        }
    }

    // Lazy walks (department.name, position.title, appUser.email) under open-in-view=false
    // require an active session — switch from SUPPORTS to a real read-only tx.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public MyProfileDtos.MyProfileResponse getMyProfile(String email) {
        Employee employee = self.ensureEmployeeForEmail(email);
        Department dept = employee.getDepartment();
        String deptName = dept != null ? dept.getName() : null;
        Employee mgr = employee.getManager();
        UUID mgrId = mgr != null ? mgr.getId() : null;
        String mgrName = mgr != null ? (mgr.getFirstName() + " " + mgr.getLastName()).trim() : null;
        Position pos = employee.getPosition();
        String jobTitle = pos != null ? pos.getTitle() : null;
        AppUser user = employee.getAppUser();
        String userEmail = user != null ? user.getEmail() : "";

        MyProfileDtos.Personal personal = new MyProfileDtos.Personal(
                employee.getPhone(),
                employee.getAddress(),
                null,
                employee.getCity(),
                employee.getState(),
                employee.getPostalCode(),
                employee.getCountry(),
                employee.getEmergencyContact(),
                employee.getEmergencyPhone()
        );

        return new MyProfileDtos.MyProfileResponse(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                userEmail,
                deptName,
                jobTitle,
                mgrId,
                mgrName,
                employee.getDateOfBirth(),
                personal
        );
    }

    @Transactional
    public MyProfileDtos.MyProfileResponse updateMyProfile(String email, MyProfileDtos.UpdateMyProfileRequest request) {
        // Full profile edits (name / DOB / job title) are HR-admin only — see EmployeeController.
        // Authenticated employees must use updatePersonalWithOtp for contact fields.
        Employee employee = self.ensureEmployeeForEmail(email);
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setUpdatedAt(LocalDateTime.now());

        if (request.personal() != null) {
            applyPersonal(employee, request.personal());
        }

        employeeRepository.save(employee);
        return self.getMyProfile(email);
    }

    public MyProfileDtos.RequestPersonalOtpResponse requestPersonalOtp(String email) {
        self.ensureEmployeeForEmail(email);
        ProfileOtpService.IssuedOtp issued = profileOtpService.issue(email, OTP_PURPOSE_PERSONAL);
        return new MyProfileDtos.RequestPersonalOtpResponse(
                issued.expiresInSeconds(),
                "Verification code sent to your account email.",
                issued.debugCode()
        );
    }

    @Transactional
    public MyProfileDtos.MyProfileResponse updatePersonalWithOtp(
            String email,
            MyProfileDtos.UpdatePersonalRequest request
    ) {
        if (request.personal() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personal details are required");
        }
        profileOtpService.consume(email, OTP_PURPOSE_PERSONAL, request.otpCode());
        Employee employee = self.ensureEmployeeForEmail(email);
        applyPersonal(employee, request.personal());
        employee.setUpdatedAt(LocalDateTime.now());
        employeeRepository.save(employee);
        return self.getMyProfile(email);
    }

    private static void applyPersonal(Employee employee, MyProfileDtos.Personal dto) {
        employee.setPhone(trimToNull(dto.phone()));
        employee.setAddress(trimToNull(dto.addressLine1()));
        employee.setCity(trimToNull(dto.city()));
        employee.setState(trimToNull(dto.state()));
        employee.setPostalCode(trimToNull(dto.postalCode()));
        employee.setCountry(trimToNull(dto.country()));
        employee.setEmergencyContact(trimToNull(dto.emergencyContactName()));
        employee.setEmergencyPhone(trimToNull(dto.emergencyContactPhone()));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<MyProfileDtos.FamilyResponse> listMyFamily(String email) {
        Employee employee = self.ensureEmployeeForEmail(email);
        return employeeFamilyRepository.findAllByEmployee_IdOrderByCreatedAtDesc(employee.getId()).stream()
                .map(this::toFamily)
                .toList();
    }

    @Transactional
    public MyProfileDtos.FamilyResponse createMyFamily(String email, MyProfileDtos.CreateFamilyRequest request) {
        Employee employee = self.ensureEmployeeForEmail(email);
        UUID orgId = requireOrgId(employee);
        EmployeeFamily row = new EmployeeFamily();
        row.setOrganizationId(orgId);
        row.setEmployee(employee);
        row.setFullName(request.name().trim());
        row.setRelationship(trimToNull(request.relationship()));
        row.setDateOfBirth(request.dateOfBirth());
        row.setPhone(trimToNull(request.phone()));
        row.setCreatedAt(LocalDateTime.now());
        return toFamily(employeeFamilyRepository.save(row));
    }

    @Transactional
    public MyProfileDtos.FamilyResponse updateMyFamily(
            String email, UUID familyId, MyProfileDtos.UpdateFamilyRequest request) {
        Employee employee = self.ensureEmployeeForEmail(email);
        EmployeeFamily row = employeeFamilyRepository
                .findByIdAndEmployee_Id(familyId, employee.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family member not found"));
        row.setFullName(request.name().trim());
        row.setRelationship(trimToNull(request.relationship()));
        row.setDateOfBirth(request.dateOfBirth());
        row.setPhone(trimToNull(request.phone()));
        return toFamily(employeeFamilyRepository.save(row));
    }

    @Transactional
    public void deleteMyFamily(String email, UUID familyId) {
        Employee employee = self.ensureEmployeeForEmail(email);
        EmployeeFamily row = employeeFamilyRepository
                .findByIdAndEmployee_Id(familyId, employee.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family member not found"));
        employeeFamilyRepository.delete(row);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<MyProfileDtos.EducationResponse> listMyEducation(String email) {
        Employee employee = self.ensureEmployeeForEmail(email);
        return employeeEducationRepository.findAllByEmployee_IdOrderByCreatedAtDesc(employee.getId()).stream()
                .map(this::toEducation)
                .toList();
    }

    @Transactional
    public MyProfileDtos.EducationResponse createMyEducation(
            String email, MyProfileDtos.CreateEducationRequest request) {
        Employee employee = self.ensureEmployeeForEmail(email);
        UUID orgId = requireOrgId(employee);
        EmployeeEducation row = new EmployeeEducation();
        row.setOrganizationId(orgId);
        row.setEmployee(employee);
        row.setInstitution(request.institution().trim());
        row.setDegree(trimToNull(request.degree()));
        row.setFieldOfStudy(trimToNull(request.fieldOfStudy()));
        row.setStartYear(request.startDate() != null ? request.startDate().getYear() : null);
        row.setEndYear(request.endDate() != null ? request.endDate().getYear() : null);
        row.setCreatedAt(LocalDateTime.now());
        return toEducation(employeeEducationRepository.save(row));
    }

    @Transactional
    public MyProfileDtos.EducationResponse updateMyEducation(
            String email, UUID educationId, MyProfileDtos.UpdateEducationRequest request) {
        Employee employee = self.ensureEmployeeForEmail(email);
        EmployeeEducation row = employeeEducationRepository
                .findByIdAndEmployee_Id(educationId, employee.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Education record not found"));
        row.setInstitution(request.institution().trim());
        row.setDegree(trimToNull(request.degree()));
        row.setFieldOfStudy(trimToNull(request.fieldOfStudy()));
        row.setStartYear(request.startDate() != null ? request.startDate().getYear() : null);
        row.setEndYear(request.endDate() != null ? request.endDate().getYear() : null);
        return toEducation(employeeEducationRepository.save(row));
    }

    @Transactional
    public void deleteMyEducation(String email, UUID educationId) {
        Employee employee = self.ensureEmployeeForEmail(email);
        EmployeeEducation row = employeeEducationRepository
                .findByIdAndEmployee_Id(educationId, employee.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Education record not found"));
        employeeEducationRepository.delete(row);
    }

    private MyProfileDtos.FamilyResponse toFamily(EmployeeFamily row) {
        return new MyProfileDtos.FamilyResponse(
                row.getId(),
                row.getFullName(),
                row.getRelationship(),
                row.getDateOfBirth(),
                row.getPhone());
    }

    private MyProfileDtos.EducationResponse toEducation(EmployeeEducation row) {
        LocalDate start = row.getStartYear() != null ? LocalDate.of(row.getStartYear(), 1, 1) : null;
        LocalDate end = row.getEndYear() != null ? LocalDate.of(row.getEndYear(), 1, 1) : null;
        return new MyProfileDtos.EducationResponse(
                row.getId(),
                row.getInstitution(),
                row.getDegree(),
                row.getFieldOfStudy(),
                start,
                end,
                null);
    }

    private UUID requireOrgId(Employee employee) {
        UUID orgId = employee.getOrganizationId();
        if (orgId == null) {
            orgId = TenantContext.get();
        }
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public MyProfileDtos.OrgChartResponse orgChart() {
        // Exclude soft-deleted employees — they'd show up in the chart with a "deleted-…" name
        // and would still be reachable as someone's manager.
        List<Employee> employees = employeeRepository.findAllByStatusNotIgnoreCase("terminated");
        List<MyProfileDtos.OrgNode> nodes = employees.stream().map(e -> {
            Department d = e.getDepartment();
            String dept = d != null ? d.getName() : null;
            UUID mgrId = e.getManager() != null ? e.getManager().getId() : null;
            String title = e.getPosition() != null ? e.getPosition().getTitle() : null;
            return new MyProfileDtos.OrgNode(
                    e.getId(),
                    (e.getFirstName() + " " + e.getLastName()).trim(),
                    title,
                    dept,
                    mgrId
            );
        }).toList();
        return new MyProfileDtos.OrgChartResponse(nodes);
    }

    private static String normalizeEmail(String raw) {
        if (raw == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        return raw.trim().toLowerCase();
    }

    private static String trimToNull(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        return t.isBlank() ? null : t;
    }

    private static String cap(String raw) {
        String t = raw.trim();
        if (t.isBlank()) return t;
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }
}
