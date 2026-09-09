package prod.tint_wym.novora_backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
import prod.tint_wym.novora_backend.entity.BonusType;
import prod.tint_wym.novora_backend.entity.Branch;
import prod.tint_wym.novora_backend.entity.DeductionType;
import prod.tint_wym.novora_backend.entity.Department;
import prod.tint_wym.novora_backend.entity.DepositType;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.Organization;
import prod.tint_wym.novora_backend.entity.OtPolicy;
import prod.tint_wym.novora_backend.entity.OvertimeRecord;
import prod.tint_wym.novora_backend.entity.PerformanceReview;
import prod.tint_wym.novora_backend.entity.Position;
import prod.tint_wym.novora_backend.entity.RosterEntry;
import prod.tint_wym.novora_backend.entity.ShiftPattern;
import prod.tint_wym.novora_backend.entity.TaxCategory;
import prod.tint_wym.novora_backend.entity.Training;
import prod.tint_wym.novora_backend.repository.AllowanceTypeRepository;
import prod.tint_wym.novora_backend.repository.AssetRepository;
import prod.tint_wym.novora_backend.repository.AttendanceRepository;
import prod.tint_wym.novora_backend.repository.AuditLogRepository;
import prod.tint_wym.novora_backend.repository.BonusTypeRepository;
import prod.tint_wym.novora_backend.repository.BranchRepository;
import prod.tint_wym.novora_backend.repository.CandidateRepository;
import prod.tint_wym.novora_backend.repository.DeductionTypeRepository;
import prod.tint_wym.novora_backend.repository.DepartmentRepository;
import prod.tint_wym.novora_backend.repository.DepositTypeRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.ExpenseClaimRepository;
import prod.tint_wym.novora_backend.repository.JobPostingRepository;
import prod.tint_wym.novora_backend.repository.LeaveRequestRepository;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;
import prod.tint_wym.novora_backend.repository.OtPolicyRepository;
import prod.tint_wym.novora_backend.repository.OvertimeRecordRepository;
import prod.tint_wym.novora_backend.repository.PayrollRepository;
import prod.tint_wym.novora_backend.repository.PerformanceReviewRepository;
import prod.tint_wym.novora_backend.repository.PositionRepository;
import prod.tint_wym.novora_backend.repository.RosterEntryRepository;
import prod.tint_wym.novora_backend.repository.ShiftPatternRepository;
import prod.tint_wym.novora_backend.repository.TaxCategoryRepository;
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
    private final BonusTypeRepository bonusTypeRepository;
    private final DeductionTypeRepository deductionTypeRepository;
    private final DepositTypeRepository depositTypeRepository;
    private final TaxCategoryRepository taxCategoryRepository;
    private final OtPolicyRepository otPolicyRepository;
    private final OvertimeRecordRepository overtimeRecordRepository;

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
            PayrollRepository payrollRepository,
            BonusTypeRepository bonusTypeRepository,
            DeductionTypeRepository deductionTypeRepository,
            DepositTypeRepository depositTypeRepository,
            TaxCategoryRepository taxCategoryRepository,
            OtPolicyRepository otPolicyRepository,
            OvertimeRecordRepository overtimeRecordRepository) {
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
        this.bonusTypeRepository = bonusTypeRepository;
        this.deductionTypeRepository = deductionTypeRepository;
        this.depositTypeRepository = depositTypeRepository;
        this.taxCategoryRepository = taxCategoryRepository;
        this.otPolicyRepository = otPolicyRepository;
        this.overtimeRecordRepository = overtimeRecordRepository;
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

    public List<WorkDtos.AttendanceLogResponse> listAttendanceRoster(LocalDate date) {
        UUID orgId = requireOrganizationId();
        return attendanceRepository.findAllByEmployee_OrganizationIdAndOptionalDate(orgId, date).stream()
                .map(this::toAttendance)
                .toList();
    }

    public List<CatalogDtos.AttendanceTodayRow> listAttendanceToday(LocalDate date) {
        UUID orgId = requireOrganizationId();
        LocalDate day = date != null ? date : LocalDate.now(ZONE);
        List<Employee> employees =
                employeeRepository.findAllByOrganizationIdAndStatusIgnoreCaseOrderByFirstNameAscLastNameAsc(
                        orgId, "active");
        Map<UUID, Attendance> attendanceByEmployee = attendanceRepository
                .findAllByEmployee_OrganizationIdAndWorkDate(orgId, day)
                .stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getId(), a -> a, (a, b) -> a));
        Map<UUID, RosterEntry> rosterByEmployee = rosterEntryRepository
                .findAllByOrganizationIdAndWorkDate(orgId, day)
                .stream()
                .collect(Collectors.toMap(r -> r.getEmployee().getId(), r -> r, (a, b) -> a));

        return employees.stream()
                .map(emp -> {
                    Attendance att = attendanceByEmployee.get(emp.getId());
                    RosterEntry roster = rosterByEmployee.get(emp.getId());
                    String shiftName = null;
                    if (roster != null && roster.getShiftPattern() != null) {
                        shiftName = roster.getShiftPattern().getName();
                    }
                    boolean onLeave = leaveRequestRepository.existsOverlappingLeave(emp.getId(), day, day);
                    String status = resolveTodayStatus(att, onLeave);
                    LocalTime checkIn = att != null && att.getCheckIn() != null ? att.getCheckIn().toLocalTime() : null;
                    LocalTime checkOut =
                            att != null && att.getCheckOut() != null ? att.getCheckOut().toLocalTime() : null;
                    BigDecimal hours = att != null ? att.getWorkHours() : null;
                    boolean officeFlag = checkIn != null;
                    String deptName = emp.getDepartment() != null ? emp.getDepartment().getName() : null;
                    return new CatalogDtos.AttendanceTodayRow(
                            emp.getId(),
                            name(emp),
                            deptName,
                            shiftName,
                            checkIn,
                            checkOut,
                            hours,
                            status,
                            officeFlag);
                })
                .toList();
    }

    private static String resolveTodayStatus(Attendance att, boolean onLeave) {
        if (att != null && att.getStatus() != null) {
            String s = att.getStatus().trim().toLowerCase(Locale.US);
            return switch (s) {
                case "late" -> "LATE";
                case "absent" -> "ABSENT";
                case "on_leave" -> "ON_LEAVE";
                case "present", "half_day" -> "PRESENT";
                default -> att.getCheckIn() != null ? "PRESENT" : (onLeave ? "ON_LEAVE" : "NOT_PUNCHED");
            };
        }
        if (onLeave) {
            return "ON_LEAVE";
        }
        return "NOT_PUNCHED";
    }

    public List<CatalogDtos.BonusTypeResponse> listBonusTypes() {
        return bonusTypeRepository.findAllByOrderByNameAsc().stream().map(this::toBonus).toList();
    }

    @Transactional
    public CatalogDtos.BonusTypeResponse createBonusType(CatalogDtos.CreateBonusTypeRequest request) {
        UUID orgId = requireOrganizationId();
        BonusType a = new BonusType();
        a.setOrganizationId(orgId);
        a.setName(request.name().trim());
        a.setCode(request.code().trim().toUpperCase(Locale.US));
        a.setAmount(request.amount());
        a.setTaxable(request.taxable() == null || request.taxable());
        a.setActive(request.active() == null || request.active());
        a.setDescription(blankToNull(request.description()));
        a.setCreatedAt(LocalDateTime.now());
        return toBonus(bonusTypeRepository.save(a));
    }

    private CatalogDtos.BonusTypeResponse toBonus(BonusType a) {
        return new CatalogDtos.BonusTypeResponse(
                a.getId(),
                a.getName(),
                a.getCode(),
                a.getAmount(),
                a.isTaxable(),
                a.isActive(),
                a.getDescription(),
                toInstant(a.getCreatedAt()));
    }

    public List<CatalogDtos.DeductionTypeResponse> listDeductionTypes() {
        return deductionTypeRepository.findAllByOrderByNameAsc().stream().map(this::toDeduction).toList();
    }

    @Transactional
    public CatalogDtos.DeductionTypeResponse createDeductionType(CatalogDtos.CreateDeductionTypeRequest request) {
        UUID orgId = requireOrganizationId();
        String freq = request.frequency() == null || request.frequency().isBlank()
                ? "monthly"
                : request.frequency().trim().toLowerCase(Locale.US);
        if (!freq.equals("monthly") && !freq.equals("one_time")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "frequency must be monthly or one_time");
        }
        DeductionType a = new DeductionType();
        a.setOrganizationId(orgId);
        a.setName(request.name().trim());
        a.setCode(request.code().trim().toUpperCase(Locale.US));
        a.setAmount(request.amount());
        a.setFrequency(freq);
        a.setActive(request.active() == null || request.active());
        a.setDescription(blankToNull(request.description()));
        a.setCreatedAt(LocalDateTime.now());
        return toDeduction(deductionTypeRepository.save(a));
    }

    private CatalogDtos.DeductionTypeResponse toDeduction(DeductionType a) {
        return new CatalogDtos.DeductionTypeResponse(
                a.getId(),
                a.getName(),
                a.getCode(),
                a.getAmount(),
                a.getFrequency(),
                a.isActive(),
                a.getDescription(),
                toInstant(a.getCreatedAt()));
    }

    public List<CatalogDtos.DepositTypeResponse> listDepositTypes() {
        return depositTypeRepository.findAllByOrderByNameAsc().stream().map(this::toDeposit).toList();
    }

    @Transactional
    public CatalogDtos.DepositTypeResponse createDepositType(CatalogDtos.CreateDepositTypeRequest request) {
        UUID orgId = requireOrganizationId();
        DepositType a = new DepositType();
        a.setOrganizationId(orgId);
        a.setName(request.name().trim());
        a.setCode(request.code().trim().toUpperCase(Locale.US));
        a.setAmount(request.amount());
        a.setRefundable(request.refundable() != null && request.refundable());
        a.setActive(request.active() == null || request.active());
        a.setDescription(blankToNull(request.description()));
        a.setCreatedAt(LocalDateTime.now());
        return toDeposit(depositTypeRepository.save(a));
    }

    private CatalogDtos.DepositTypeResponse toDeposit(DepositType a) {
        return new CatalogDtos.DepositTypeResponse(
                a.getId(),
                a.getName(),
                a.getCode(),
                a.getAmount(),
                a.isRefundable(),
                a.isActive(),
                a.getDescription(),
                toInstant(a.getCreatedAt()));
    }

    public List<CatalogDtos.TaxCategoryResponse> listTaxCategories() {
        return taxCategoryRepository.findAllByOrderByNameAsc().stream().map(this::toTax).toList();
    }

    @Transactional
    public CatalogDtos.TaxCategoryResponse createTaxCategory(CatalogDtos.CreateTaxCategoryRequest request) {
        UUID orgId = requireOrganizationId();
        TaxCategory a = new TaxCategory();
        a.setOrganizationId(orgId);
        a.setName(request.name().trim());
        a.setCode(request.code().trim().toUpperCase(Locale.US));
        a.setRate(request.rate() != null ? request.rate() : BigDecimal.ZERO);
        a.setActive(request.active() == null || request.active());
        a.setDescription(blankToNull(request.description()));
        a.setCreatedAt(LocalDateTime.now());
        return toTax(taxCategoryRepository.save(a));
    }

    private CatalogDtos.TaxCategoryResponse toTax(TaxCategory a) {
        return new CatalogDtos.TaxCategoryResponse(
                a.getId(),
                a.getName(),
                a.getCode(),
                a.getRate(),
                a.isActive(),
                a.getDescription(),
                toInstant(a.getCreatedAt()));
    }

    public List<CatalogDtos.OtPolicyResponse> listOtPolicies() {
        return otPolicyRepository.findAllByOrderByNameAsc().stream().map(this::toOtPolicy).toList();
    }

    @Transactional
    public CatalogDtos.OtPolicyResponse createOtPolicy(CatalogDtos.CreateOtPolicyRequest request) {
        UUID orgId = requireOrganizationId();
        OtPolicy p = new OtPolicy();
        p.setOrganizationId(orgId);
        p.setName(request.name().trim());
        p.setWeekdayMultiplier(
                request.weekdayMultiplier() != null ? request.weekdayMultiplier() : new BigDecimal("1.50"));
        p.setWeekendMultiplier(
                request.weekendMultiplier() != null ? request.weekendMultiplier() : new BigDecimal("2.00"));
        p.setHolidayMultiplier(
                request.holidayMultiplier() != null ? request.holidayMultiplier() : new BigDecimal("3.00"));
        p.setDailyThresholdHours(
                request.dailyThresholdHours() != null ? request.dailyThresholdHours() : new BigDecimal("8.00"));
        p.setRequiresApproval(request.requiresApproval() == null || request.requiresApproval());
        p.setActive(request.active() == null || request.active());
        p.setNotes(blankToNull(request.notes()));
        p.setCreatedAt(LocalDateTime.now());
        return toOtPolicy(otPolicyRepository.save(p));
    }

    @Transactional
    public CatalogDtos.OtPolicyResponse updateOtPolicy(UUID id, CatalogDtos.UpdateOtPolicyRequest request) {
        requireOrganizationId();
        OtPolicy p = otPolicyRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OT policy not found"));
        if (request.name() != null && !request.name().isBlank()) {
            p.setName(request.name().trim());
        }
        if (request.weekdayMultiplier() != null) {
            p.setWeekdayMultiplier(request.weekdayMultiplier());
        }
        if (request.weekendMultiplier() != null) {
            p.setWeekendMultiplier(request.weekendMultiplier());
        }
        if (request.holidayMultiplier() != null) {
            p.setHolidayMultiplier(request.holidayMultiplier());
        }
        if (request.dailyThresholdHours() != null) {
            p.setDailyThresholdHours(request.dailyThresholdHours());
        }
        if (request.requiresApproval() != null) {
            p.setRequiresApproval(request.requiresApproval());
        }
        if (request.active() != null) {
            p.setActive(request.active());
        }
        if (request.notes() != null) {
            p.setNotes(blankToNull(request.notes()));
        }
        return toOtPolicy(otPolicyRepository.save(p));
    }

    private CatalogDtos.OtPolicyResponse toOtPolicy(OtPolicy p) {
        return new CatalogDtos.OtPolicyResponse(
                p.getId(),
                p.getName(),
                p.getWeekdayMultiplier(),
                p.getWeekendMultiplier(),
                p.getHolidayMultiplier(),
                p.getDailyThresholdHours(),
                p.isRequiresApproval(),
                p.isActive(),
                p.getNotes(),
                toInstant(p.getCreatedAt()));
    }

    public List<CatalogDtos.OvertimeRecordResponse> listOvertimeRecords(
            LocalDate from, LocalDate to, String status) {
        UUID orgId = requireOrganizationId();
        LocalDate fromDate = from != null ? from : LocalDate.now(ZONE).minusMonths(1);
        LocalDate toDate = to != null ? to : LocalDate.now(ZONE);
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toLowerCase(Locale.US);
        return overtimeRecordRepository.findAllFiltered(orgId, fromDate, toDate, statusFilter).stream()
                .map(this::toOvertime)
                .toList();
    }

    @Transactional
    public CatalogDtos.OvertimeRecordResponse createOvertime(CatalogDtos.CreateOvertimeRequest request) {
        UUID orgId = requireOrganizationId();
        Employee employee = employeeRepository
                .findByIdAndOrganizationId(request.employeeId(), orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (request.hours().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hours must be positive");
        }
        OvertimeRecord o = new OvertimeRecord();
        o.setOrganizationId(orgId);
        o.setEmployee(employee);
        o.setWorkDate(request.workDate());
        o.setStartTime(blankToNull(request.startTime()));
        o.setEndTime(blankToNull(request.endTime()));
        o.setHours(request.hours());
        o.setReason(blankToNull(request.reason()));
        o.setStatus("pending");
        o.setCreatedAt(LocalDateTime.now());
        return toOvertime(overtimeRecordRepository.save(o));
    }

    @Transactional
    public CatalogDtos.OvertimeRecordResponse decideOvertime(
            String adminEmail, UUID id, CatalogDtos.DecideOvertimeRequest request) {
        UUID orgId = requireOrganizationId();
        OvertimeRecord o = overtimeRecordRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Overtime record not found"));
        if (!orgId.equals(o.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Overtime record not found");
        }
        if (!"pending".equalsIgnoreCase(o.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Overtime already decided");
        }
        String decision = request.decision().trim().toUpperCase(Locale.US);
        if (!decision.equals("APPROVE") && !decision.equals("REJECT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be APPROVE or REJECT");
        }
        o.setStatus(decision.equals("APPROVE") ? "approved" : "rejected");
        o.setDecisionNote(blankToNull(request.note()));
        employeeRepository
                .findByAppUser_EmailIgnoreCase(adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.US))
                .ifPresent(decider -> o.setDecidedBy(decider.getId()));
        return toOvertime(overtimeRecordRepository.save(o));
    }

    private CatalogDtos.OvertimeRecordResponse toOvertime(OvertimeRecord o) {
        return new CatalogDtos.OvertimeRecordResponse(
                o.getId(),
                o.getEmployee().getId(),
                name(o.getEmployee()),
                o.getWorkDate(),
                o.getStartTime(),
                o.getEndTime(),
                o.getHours(),
                o.getReason(),
                o.getStatus() == null ? "" : o.getStatus().toUpperCase(Locale.US),
                o.getDecidedBy(),
                o.getDecisionNote(),
                toInstant(o.getCreatedAt()));
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
