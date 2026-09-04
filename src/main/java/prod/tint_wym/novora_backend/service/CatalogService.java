package prod.tint_wym.novora_backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.CatalogDtos;
import prod.tint_wym.novora_backend.dto.WorkDtos;
import prod.tint_wym.novora_backend.entity.AllowanceType;
import prod.tint_wym.novora_backend.entity.Asset;
import prod.tint_wym.novora_backend.entity.Attendance;
import prod.tint_wym.novora_backend.entity.Branch;
import prod.tint_wym.novora_backend.entity.Department;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.Organization;
import prod.tint_wym.novora_backend.entity.PerformanceReview;
import prod.tint_wym.novora_backend.entity.Position;
import prod.tint_wym.novora_backend.entity.RosterEntry;
import prod.tint_wym.novora_backend.entity.ShiftPattern;
import prod.tint_wym.novora_backend.entity.Training;
import prod.tint_wym.novora_backend.repository.AllowanceTypeRepository;
import prod.tint_wym.novora_backend.repository.AssetRepository;
import prod.tint_wym.novora_backend.repository.AttendanceRepository;
import prod.tint_wym.novora_backend.repository.AuditLogRepository;
import prod.tint_wym.novora_backend.repository.BranchRepository;
import prod.tint_wym.novora_backend.repository.CandidateRepository;
import prod.tint_wym.novora_backend.repository.DepartmentRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.ExpenseClaimRepository;
import prod.tint_wym.novora_backend.repository.JobPostingRepository;
import prod.tint_wym.novora_backend.repository.LeaveRequestRepository;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;
import prod.tint_wym.novora_backend.repository.PayrollRepository;
import prod.tint_wym.novora_backend.repository.PerformanceReviewRepository;
import prod.tint_wym.novora_backend.repository.PositionRepository;
import prod.tint_wym.novora_backend.repository.RosterEntryRepository;
import prod.tint_wym.novora_backend.repository.ShiftPatternRepository;
import prod.tint_wym.novora_backend.repository.TrainingRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final AllowanceTypeRepository allowanceTypeRepository;
    private final ShiftPatternRepository shiftPatternRepository;
    private final RosterEntryRepository rosterEntryRepository;
    private final AttendanceRepository attendanceRepository;
    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final AssetRepository assetRepository;
    private final TrainingRepository trainingRepository;
    private final PerformanceReviewRepository performanceReviewRepository;
    private final AuditLogRepository auditLogRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CandidateRepository candidateRepository;
    private final ExpenseClaimRepository expenseClaimRepository;
    private final PayrollRepository payrollRepository;

    public CatalogService(
            AllowanceTypeRepository allowanceTypeRepository,
            ShiftPatternRepository shiftPatternRepository,
            RosterEntryRepository rosterEntryRepository,
            AttendanceRepository attendanceRepository,
            PositionRepository positionRepository,
            DepartmentRepository departmentRepository,
            OrganizationRepository organizationRepository,
            BranchRepository branchRepository,
            AssetRepository assetRepository,
            TrainingRepository trainingRepository,
            PerformanceReviewRepository performanceReviewRepository,
            AuditLogRepository auditLogRepository,
            EmployeeRepository employeeRepository,
            LeaveRequestRepository leaveRequestRepository,
            JobPostingRepository jobPostingRepository,
            CandidateRepository candidateRepository,
            ExpenseClaimRepository expenseClaimRepository,
            PayrollRepository payrollRepository) {
        this.allowanceTypeRepository = allowanceTypeRepository;
        this.shiftPatternRepository = shiftPatternRepository;
        this.rosterEntryRepository = rosterEntryRepository;
        this.attendanceRepository = attendanceRepository;
        this.positionRepository = positionRepository;
        this.departmentRepository = departmentRepository;
        this.organizationRepository = organizationRepository;
        this.branchRepository = branchRepository;
        this.assetRepository = assetRepository;
        this.trainingRepository = trainingRepository;
        this.performanceReviewRepository = performanceReviewRepository;
        this.auditLogRepository = auditLogRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.candidateRepository = candidateRepository;
        this.expenseClaimRepository = expenseClaimRepository;
        this.payrollRepository = payrollRepository;
    }

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZONE).toInstant();
    }

    private static String name(Employee e) {
        return (e.getFirstName() + " " + e.getLastName()).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public List<CatalogDtos.AllowanceTypeResponse> listAllowanceTypes() {
        return allowanceTypeRepository.findAllByOrderByNameAsc().stream()
                .map(a -> new CatalogDtos.AllowanceTypeResponse(
                        a.getId(),
                        a.getName(),
                        a.getCode(),
                        a.getAmount(),
                        a.getFrequency(),
                        a.isTaxable(),
                        a.isActive(),
                        a.getDescription(),
                        toInstant(a.getCreatedAt())))
                .toList();
    }

    @Transactional
    public CatalogDtos.AllowanceTypeResponse createAllowanceType(CatalogDtos.CreateAllowanceTypeRequest request) {
        UUID orgId = requireOrganizationId();
        String freq = request.frequency() == null || request.frequency().isBlank()
                ? "monthly"
                : request.frequency().trim().toLowerCase(Locale.US);
        if (!freq.equals("monthly") && !freq.equals("one_time")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "frequency must be monthly or one_time");
        }
        AllowanceType a = new AllowanceType();
        a.setOrganizationId(orgId);
        a.setName(request.name().trim());
        a.setCode(request.code().trim().toUpperCase(Locale.US));
        a.setAmount(request.amount());
        a.setFrequency(freq);
        a.setTaxable(request.taxable() == null || request.taxable());
        a.setActive(request.active() == null || request.active());
        a.setDescription(blankToNull(request.description()));
        a.setCreatedAt(LocalDateTime.now());
        AllowanceType saved = allowanceTypeRepository.save(a);
        return new CatalogDtos.AllowanceTypeResponse(
                saved.getId(),
                saved.getName(),
                saved.getCode(),
                saved.getAmount(),
                saved.getFrequency(),
                saved.isTaxable(),
                saved.isActive(),
                saved.getDescription(),
                toInstant(saved.getCreatedAt()));
    }

    public List<CatalogDtos.ShiftPatternResponse> listShiftPatterns() {
        return shiftPatternRepository.findAllByOrderByNameAsc().stream()
                .map(s -> new CatalogDtos.ShiftPatternResponse(
                        s.getId(),
                        s.getName(),
                        s.getStartTime(),
                        s.getEndTime(),
                        s.getBreakMins(),
                        s.getColor(),
                        s.isActive(),
                        toInstant(s.getCreatedAt())))
                .toList();
    }

    @Transactional
    public CatalogDtos.ShiftPatternResponse createShiftPattern(CatalogDtos.CreateShiftPatternRequest request) {
        UUID orgId = requireOrganizationId();
        ShiftPattern s = new ShiftPattern();
        s.setOrganizationId(orgId);
        s.setName(request.name().trim());
        s.setStartTime(request.startTime());
        s.setEndTime(request.endTime());
        s.setBreakMins(request.breakMins() != null ? request.breakMins() : 60);
        s.setColor(blankToNull(request.color()));
        s.setActive(request.active() == null || request.active());
        s.setCreatedAt(LocalDateTime.now());
        ShiftPattern saved = shiftPatternRepository.save(s);
        return new CatalogDtos.ShiftPatternResponse(
                saved.getId(),
                saved.getName(),
                saved.getStartTime(),
                saved.getEndTime(),
                saved.getBreakMins(),
                saved.getColor(),
                saved.isActive(),
                toInstant(saved.getCreatedAt()));
    }

    public List<CatalogDtos.RosterEntryResponse> listRoster(LocalDate from, LocalDate to) {
        List<RosterEntry> rows;
        if (from != null && to != null) {
            rows = rosterEntryRepository.findAllByWorkDateBetweenOrderByWorkDateAsc(from, to);
        } else {
            rows = rosterEntryRepository.findAllByOrderByWorkDateDesc();
        }
        return rows.stream().map(this::toRoster).toList();
    }

    @Transactional
    public CatalogDtos.RosterEntryResponse createRoster(CatalogDtos.CreateRosterEntryRequest request) {
        UUID orgId = requireOrganizationId();
        Employee employee = employeeRepository
                .findByIdAndOrganizationId(request.employeeId(), orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        ShiftPattern pattern = null;
        if (request.shiftPatternId() != null) {
            pattern = shiftPatternRepository
                    .findById(request.shiftPatternId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift pattern not found"));
        }
        RosterEntry entry = new RosterEntry();
        entry.setOrganizationId(orgId);
        entry.setEmployee(employee);
        entry.setWorkDate(request.workDate());
        entry.setShiftPattern(pattern);
        entry.setStatus(
                request.status() == null || request.status().isBlank()
                        ? "scheduled"
                        : request.status().trim().toLowerCase(Locale.US));
        entry.setNotes(blankToNull(request.notes()));
        entry.setCreatedAt(LocalDateTime.now());
        return toRoster(rosterEntryRepository.save(entry));
    }

    private CatalogDtos.RosterEntryResponse toRoster(RosterEntry e) {
        ShiftPattern sp = e.getShiftPattern();
        return new CatalogDtos.RosterEntryResponse(
                e.getId(),
                e.getEmployee().getId(),
                name(e.getEmployee()),
                e.getWorkDate(),
                sp != null ? sp.getId() : null,
                sp != null ? sp.getName() : null,
                e.getStatus(),
                e.getNotes(),
                toInstant(e.getCreatedAt()));
    }

    public List<WorkDtos.AttendanceLogResponse> listAttendanceRoster() {
        return attendanceRepository.findAllByOrderByWorkDateDesc().stream()
                .map(this::toAttendance)
                .toList();
    }

    private WorkDtos.AttendanceLogResponse toAttendance(Attendance log) {
        return new WorkDtos.AttendanceLogResponse(
                log.getId(),
                log.getEmployee().getId(),
                log.getWorkDate(),
                log.getStatus() == null ? "" : log.getStatus().toUpperCase(Locale.US),
                log.getCheckIn() == null ? null : log.getCheckIn().toLocalTime(),
                log.getCheckOut() == null ? null : log.getCheckOut().toLocalTime(),
                log.getWorkHours(),
                log.getNotes());
    }

    public List<CatalogDtos.PositionResponse> listPositions() {
        return positionRepository.findAllByOrderByTitleAsc().stream().map(this::toPosition).toList();
    }

    @Transactional
    public CatalogDtos.PositionResponse createPosition(CatalogDtos.CreatePositionRequest request) {
        UUID orgId = requireOrganizationId();
        Department department = resolveDepartment(request.departmentId(), request.departmentName(), orgId);
        LocalDateTime now = LocalDateTime.now();
        Position p = new Position();
        p.setOrganizationId(orgId);
        p.setTitle(request.title().trim());
        p.setDepartment(department);
        p.setLevel(blankToNull(request.level()));
        p.setMinSalary(request.minSalary());
        p.setMaxSalary(request.maxSalary());
        p.setActive(request.active() == null || request.active());
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return toPosition(positionRepository.save(p));
    }

    private CatalogDtos.PositionResponse toPosition(Position p) {
        Department d = p.getDepartment();
        return new CatalogDtos.PositionResponse(
                p.getId(),
                p.getTitle(),
                d != null ? d.getId() : null,
                d != null ? d.getName() : null,
                p.getLevel(),
                p.getMinSalary(),
                p.getMaxSalary(),
                p.isActive(),
                toInstant(p.getCreatedAt()));
    }

    private Department resolveDepartment(UUID departmentId, String departmentName, UUID orgId) {
        if (departmentId != null) {
            return departmentRepository
                    .findById(departmentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        }
        if (departmentName != null && !departmentName.isBlank()) {
            return departmentRepository
                    .findByNameIgnoreCase(departmentName.trim())
                    .orElseGet(() -> createDepartment(departmentName.trim(), orgId));
        }
        return departmentRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> createDepartment("General", orgId));
    }

    private Department createDepartment(String name, UUID orgId) {
        LocalDateTime now = LocalDateTime.now();
        Department d = new Department();
        d.setOrganizationId(orgId);
        d.setName(name);
        String code = name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.US);
        if (code.length() > 8) {
            code = code.substring(0, 8);
        }
        if (code.isBlank()) {
            code = "DEPT";
        }
        d.setCode(code);
        d.setActive(true);
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        return departmentRepository.save(d);
    }

    public CatalogDtos.OrganizationProfileResponse getOrganization() {
        UUID orgId = requireOrganizationId();
        Organization o = organizationRepository
                .findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        return toOrg(o);
    }

    @Transactional
    public CatalogDtos.OrganizationProfileResponse updateOrganization(
            CatalogDtos.UpdateOrganizationProfileRequest request) {
        UUID orgId = requireOrganizationId();
        Organization o = organizationRepository
                .findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        if (request.name() != null && !request.name().isBlank()) {
            o.setName(request.name().trim());
        }
        if (request.legalName() != null) {
            o.setLegalName(blankToNull(request.legalName()));
        }
        if (request.registrationNo() != null) {
            o.setRegistrationNo(blankToNull(request.registrationNo()));
        }
        if (request.addressLine1() != null) {
            o.setAddressLine1(blankToNull(request.addressLine1()));
        }
        if (request.city() != null) {
            o.setCity(blankToNull(request.city()));
        }
        if (request.country() != null) {
            o.setCountry(blankToNull(request.country()));
        }
        if (request.phone() != null) {
            o.setPhone(blankToNull(request.phone()));
        }
        if (request.website() != null) {
            o.setWebsite(blankToNull(request.website()));
        }
        return toOrg(organizationRepository.save(o));
    }

    private static CatalogDtos.OrganizationProfileResponse toOrg(Organization o) {
        return new CatalogDtos.OrganizationProfileResponse(
                o.getId(),
                o.getName(),
                o.getSlug(),
                o.getLegalName(),
                o.getRegistrationNo(),
                o.getAddressLine1(),
                o.getCity(),
                o.getCountry(),
                o.getPhone(),
                o.getWebsite());
    }

    public List<CatalogDtos.BranchResponse> listBranches() {
        return branchRepository.findAllByOrderByNameAsc().stream()
                .map(b -> new CatalogDtos.BranchResponse(
                        b.getId(),
                        b.getName(),
                        b.getCity(),
                        b.getAddress(),
                        b.getHeadcount(),
                        b.isActive(),
                        toInstant(b.getCreatedAt())))
                .toList();
    }

    @Transactional
    public CatalogDtos.BranchResponse createBranch(CatalogDtos.CreateBranchRequest request) {
        UUID orgId = requireOrganizationId();
        Branch b = new Branch();
        b.setOrganizationId(orgId);
        b.setName(request.name().trim());
        b.setCity(blankToNull(request.city()));
        b.setAddress(blankToNull(request.address()));
        b.setHeadcount(request.headcount() != null ? request.headcount() : 0);
        b.setActive(request.active() == null || request.active());
        b.setCreatedAt(LocalDateTime.now());
        Branch saved = branchRepository.save(b);
        return toBranch(saved);
    }

    @Transactional
    public CatalogDtos.BranchResponse updateBranch(UUID branchId, CatalogDtos.UpdateBranchRequest request) {
        requireOrganizationId();
        Branch b = branchRepository
                .findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
        if (request.name() != null && !request.name().isBlank()) {
            b.setName(request.name().trim());
        }
        if (request.city() != null) {
            b.setCity(blankToNull(request.city()));
        }
        if (request.address() != null) {
            b.setAddress(blankToNull(request.address()));
        }
        if (request.headcount() != null) {
            b.setHeadcount(request.headcount());
        }
        if (request.active() != null) {
            b.setActive(request.active());
        }
        return toBranch(branchRepository.save(b));
    }

    private CatalogDtos.BranchResponse toBranch(Branch b) {
        return new CatalogDtos.BranchResponse(
                b.getId(),
                b.getName(),
                b.getCity(),
                b.getAddress(),
                b.getHeadcount(),
                b.isActive(),
                toInstant(b.getCreatedAt()));
    }

    public List<CatalogDtos.AssetResponse> listAssets() {
        return assetRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toAsset).toList();
    }

    @Transactional
    public CatalogDtos.AssetResponse createAsset(CatalogDtos.CreateAssetRequest request) {
        UUID orgId = requireOrganizationId();
        LocalDateTime now = LocalDateTime.now();
        Asset a = new Asset();
        a.setOrganizationId(orgId);
        a.setName(request.name().trim());
        a.setAssetCode(request.assetCode().trim());
        a.setCategory(blankToNull(request.category()));
        a.setBrand(blankToNull(request.brand()));
        a.setModel(blankToNull(request.model()));
        a.setSerialNumber(blankToNull(request.serialNumber()));
        a.setPurchaseDate(request.purchaseDate());
        a.setPurchasePrice(request.purchasePrice());
        if (request.assignedToId() != null) {
            Employee emp = employeeRepository
                    .findByIdAndOrganizationId(request.assignedToId(), orgId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
            a.setAssignedTo(emp);
            a.setAssignedDate(LocalDate.now());
        }
        a.setAssetCondition(
                request.assetCondition() == null || request.assetCondition().isBlank()
                        ? "good"
                        : request.assetCondition().trim().toLowerCase(Locale.US));
        a.setLocation(blankToNull(request.location()));
        a.setNotes(blankToNull(request.notes()));
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        return toAsset(assetRepository.save(a));
    }

    private CatalogDtos.AssetResponse toAsset(Asset a) {
        Employee assigned = a.getAssignedTo();
        return new CatalogDtos.AssetResponse(
                a.getId(),
                a.getName(),
                a.getAssetCode(),
                a.getCategory(),
                a.getBrand(),
                a.getModel(),
                a.getSerialNumber(),
                a.getPurchaseDate(),
                a.getPurchasePrice(),
                assigned != null ? assigned.getId() : null,
                assigned != null ? name(assigned) : null,
                a.getAssetCondition(),
                a.getLocation(),
                a.getNotes(),
                toInstant(a.getCreatedAt()));
    }

    public List<CatalogDtos.TrainingResponse> listTrainings() {
        return trainingRepository.findAllByOrderByStartDateDesc().stream().map(this::toTraining).toList();
    }

    @Transactional
    public CatalogDtos.TrainingResponse createTraining(CatalogDtos.CreateTrainingRequest request) {
        UUID orgId = requireOrganizationId();
        LocalDateTime now = LocalDateTime.now();
        Training t = new Training();
        t.setOrganizationId(orgId);
        t.setTitle(request.title().trim());
        t.setDescription(blankToNull(request.description()));
        t.setCategory(blankToNull(request.category()));
        t.setTrainer(blankToNull(request.trainer()));
        t.setLocation(blankToNull(request.location()));
        t.setMode(blankToNull(request.mode()));
        t.setStartDate(request.startDate());
        t.setEndDate(request.endDate());
        t.setDurationHours(request.durationHours());
        t.setMaxParticipants(request.maxParticipants());
        t.setCost(request.cost());
        t.setStatus(
                request.status() == null || request.status().isBlank()
                        ? "scheduled"
                        : request.status().trim().toLowerCase(Locale.US));
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        return toTraining(trainingRepository.save(t));
    }

    private CatalogDtos.TrainingResponse toTraining(Training t) {
        return new CatalogDtos.TrainingResponse(
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                t.getCategory(),
                t.getTrainer(),
                t.getLocation(),
                t.getMode(),
                t.getStartDate(),
                t.getEndDate(),
                t.getDurationHours(),
                t.getMaxParticipants(),
                t.getCost(),
                t.getStatus(),
                toInstant(t.getCreatedAt()));
    }

    public List<CatalogDtos.PerformanceReviewResponse> listPerformanceReviews() {
        UUID orgId = requireOrganizationId();
        return performanceReviewRepository.findAllByEmployee_OrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .map(this::toReview)
                .toList();
    }

    @Transactional
    public CatalogDtos.PerformanceReviewResponse createPerformanceReview(
            String adminEmail, CatalogDtos.CreatePerformanceReviewRequest request) {
        UUID orgId = requireOrganizationId();
        Employee employee = employeeRepository
                .findByIdAndOrganizationId(request.employeeId(), orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        Employee reviewer;
        if (request.reviewerId() != null) {
            reviewer = employeeRepository
                    .findByIdAndOrganizationId(request.reviewerId(), orgId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reviewer not found"));
        } else {
            reviewer = employeeRepository
                    .findByAppUser_EmailIgnoreCase(adminEmail.trim().toLowerCase(Locale.US))
                    .orElse(employee);
        }
        LocalDateTime now = LocalDateTime.now();
        PerformanceReview pr = new PerformanceReview();
        pr.setEmployee(employee);
        pr.setReviewer(reviewer);
        pr.setReviewYear(request.reviewYear());
        pr.setReviewQuarter(request.reviewQuarter());
        pr.setReviewType(blankToNull(request.reviewType()));
        pr.setScore(request.score());
        pr.setRating(blankToNull(request.rating()));
        pr.setGoals(blankToNull(request.goals()));
        pr.setComments(blankToNull(request.comments()));
        pr.setStatus(
                request.status() == null || request.status().isBlank()
                        ? "draft"
                        : request.status().trim().toLowerCase(Locale.US));
        pr.setCreatedAt(now);
        pr.setUpdatedAt(now);
        return toReview(performanceReviewRepository.save(pr));
    }

    private CatalogDtos.PerformanceReviewResponse toReview(PerformanceReview pr) {
        return new CatalogDtos.PerformanceReviewResponse(
                pr.getId(),
                pr.getEmployee().getId(),
                name(pr.getEmployee()),
                pr.getReviewer().getId(),
                name(pr.getReviewer()),
                pr.getReviewYear(),
                pr.getReviewQuarter(),
                pr.getReviewType(),
                pr.getScore(),
                pr.getRating(),
                pr.getStatus(),
                toInstant(pr.getCreatedAt()));
    }

    public List<CatalogDtos.AuditLogResponse> listAuditLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100)).stream()
                .map(a -> new CatalogDtos.AuditLogResponse(
                        a.getId(),
                        a.getAction(),
                        a.getTableName(),
                        a.getRecordId(),
                        a.getUser() != null ? a.getUser().getEmail() : null,
                        toInstant(a.getCreatedAt())))
                .toList();
    }

    public CatalogDtos.ReportSummaryResponse reportSummary() {
        UUID orgId = requireOrganizationId();
        LocalDate now = LocalDate.now(ZONE);
        return new CatalogDtos.ReportSummaryResponse(
                employeeRepository.countByStatusNotIgnoreCase("terminated"),
                leaveRequestRepository.countByStatusAndEmployee_OrganizationId("pending", orgId),
                jobPostingRepository.countByStatusIgnoreCase("open"),
                candidateRepository.count(),
                expenseClaimRepository.countByStatusIgnoreCase("pending"),
                payrollRepository.countByPayYearAndPayMonth(now.getYear(), now.getMonthValue()));
    }
}
