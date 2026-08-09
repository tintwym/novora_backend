package prod.tint_wym.novora_backend.service;

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.entity.Attendance;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.HrDocument;
import prod.tint_wym.novora_backend.entity.LeaveBalance;
import prod.tint_wym.novora_backend.entity.LeaveRequest;
import prod.tint_wym.novora_backend.entity.LeaveType;
import prod.tint_wym.novora_backend.dto.WorkDtos;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.repository.AttendanceRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.HrDocumentRepository;
import prod.tint_wym.novora_backend.repository.LeaveBalanceRepository;
import prod.tint_wym.novora_backend.repository.LeaveRequestRepository;
import prod.tint_wym.novora_backend.repository.LeaveTypeRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

/**
 * Class-level {@code @Transactional(readOnly = true)} is intentional: with
 * {@code spring.jpa.open-in-view=false} every read method that walks a lazy association (e.g.
 * {@code lr.getLeaveType().getName()} in {@link #toLeave}) would otherwise throw
 * {@code LazyInitializationException} because the Hibernate session would already be closed by
 * the time the controller serializes the response. Write methods override the default with the
 * non-readOnly {@code @Transactional} below so flushes are not blocked.
 */
@Service
@Transactional(readOnly = true)
public class WorkService {

    /** DB CHECK constraints from V10__novora_schema_v1_1.sql — keep these in sync. */
    private static final Set<String> ALLOWED_ATTENDANCE_STATUSES =
            Set.of("present", "absent", "late", "half_day", "on_leave", "holiday", "weekend");
    private static final Set<String> ALLOWED_DOC_TYPES = Set.of(
            "contract", "id_card", "certificate", "payslip", "offer_letter",
            "nda", "warning_letter", "appraisal", "other");

    private static final ZoneId ZONE = ZoneId.systemDefault();
    /** Expected start of day for simple "late" detection (Zoho People-style default shift). */
    private static final LocalTime DEFAULT_SHIFT_START = LocalTime.of(9, 0);

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AttendanceRepository attendanceRepository;
    private final HrDocumentRepository hrDocumentRepository;
    private final AppUserRepository appUserRepository;

    public WorkService(
            EmployeeRepository employeeRepository,
            LeaveRequestRepository leaveRequestRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            AttendanceRepository attendanceRepository,
            HrDocumentRepository hrDocumentRepository,
            AppUserRepository appUserRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.attendanceRepository = attendanceRepository;
        this.hrDocumentRepository = hrDocumentRepository;
        this.appUserRepository = appUserRepository;
    }

    private static String name(Employee e) {
        return (e.getFirstName() + " " + e.getLastName()).trim();
    }

    private Employee employeeForEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.US);
        return employeeRepository.findByAppUser_EmailIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found for user"));
    }

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    private Employee requireEmployeeInOrg(UUID employeeId) {
        UUID orgId = requireOrganizationId();
        return employeeRepository.findByIdAndOrganizationId(employeeId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    public List<WorkDtos.LeaveRequestResponse> myLeave(String email) {
        Employee e = employeeForEmail(email);
        return leaveRequestRepository.findAllByEmployee_IdOrderByCreatedAtDesc(e.getId()).stream()
                .map(lr -> toLeave(lr, e))
                .toList();
    }

    @Transactional
    public WorkDtos.LeaveRequestResponse createMyLeave(String email, WorkDtos.CreateLeaveRequest request) {
        Employee e = employeeForEmail(email);
        LeaveType lt = resolveLeaveType(request.leaveType());
        if (!lt.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave type is inactive");
        }
        // Without this guard, a swapped date range produces a negative `total_days` which
        // (a) fails the BigDecimal precision=5 constraint inconsistently across drivers and
        // (b) corrupts leave-balance accounting downstream. The DTO already @NotNull-s both
        // dates so we only have to validate order here.
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "End date must be on or after start date");
        }
        if (leaveRequestRepository.existsOverlappingLeave(e.getId(), request.startDate(), request.endDate())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Overlapping pending or approved leave already exists for these dates");
        }
        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        BigDecimal totalDays = BigDecimal.valueOf(days);
        int year = request.startDate().getYear();
        LeaveBalance balance = requireBalanceWithAvailability(e, lt, year, totalDays);

        LocalDateTime now = LocalDateTime.now();
        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(e);
        lr.setLeaveType(lt);
        lr.setStartDate(request.startDate());
        lr.setEndDate(request.endDate());
        lr.setTotalDays(totalDays);
        lr.setReason(request.reason() == null ? null : request.reason().trim());
        lr.setStatus("pending");
        lr.setCreatedAt(now);
        lr.setUpdatedAt(now);
        LeaveRequest saved = leaveRequestRepository.save(lr);

        balance.setPendingDays(nz(balance.getPendingDays()).add(totalDays));
        recomputeRemaining(balance);
        balance.setUpdatedAt(now);
        leaveBalanceRepository.save(balance);

        return toLeave(saved, e);
    }

    @Transactional
    public void cancelMyLeave(String email, UUID leaveId) {
        Employee e = employeeForEmail(email);
        LeaveRequest lr = leaveRequestRepository.findByIdAndEmployee_Id(leaveId, e.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (!"pending".equalsIgnoreCase(lr.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending leave can be cancelled");
        }
        LocalDateTime now = LocalDateTime.now();
        releasePendingDays(lr, now);
        lr.setStatus("cancelled");
        lr.setUpdatedAt(now);
        leaveRequestRepository.save(lr);
    }

    public List<WorkDtos.LeaveRequestResponse> pendingLeave() {
        UUID orgId = requireOrganizationId();
        return leaveRequestRepository.findAllByStatusAndEmployee_OrganizationIdOrderByCreatedAtDesc("pending", orgId).stream()
                .map(lr -> toLeave(lr, lr.getEmployee()))
                .toList();
    }

    @Transactional
    public WorkDtos.LeaveRequestResponse decideLeave(String decidedByEmail, UUID leaveId, WorkDtos.DecideLeaveRequest request) {
        UUID orgId = requireOrganizationId();
        LeaveRequest lr = leaveRequestRepository.findByIdAndEmployee_OrganizationId(leaveId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (!"pending".equalsIgnoreCase(lr.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Leave request already decided");
        }
        String decision = request.decision() == null ? "" : request.decision().trim().toUpperCase(Locale.US);
        Employee decider = employeeRepository
                .findByAppUser_EmailIgnoreCase(decidedByEmail.trim().toLowerCase(Locale.US))
                .orElse(null);

        if (decider != null && decider.getId().equals(lr.getEmployee().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot approve or reject your own leave");
        }

        LocalDateTime now = LocalDateTime.now();
        if ("APPROVE".equals(decision)) {
            lr.setStatus("approved");
            lr.setApprovedBy(decider);
            lr.setApprovedAt(now);
            lr.setRejectionNote(null);
            movePendingToUsed(lr, now);
        } else if ("REJECT".equals(decision)) {
            lr.setStatus("rejected");
            lr.setApprovedBy(decider);
            lr.setApprovedAt(now);
            lr.setRejectionNote(request.note() == null ? null : request.note().trim());
            releasePendingDays(lr, now);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be APPROVE or REJECT");
        }
        lr.setUpdatedAt(now);
        LeaveRequest saved = leaveRequestRepository.save(lr);
        return toLeave(saved, saved.getEmployee());
    }

    private LeaveBalance requireBalanceWithAvailability(
            Employee employee, LeaveType leaveType, int year, BigDecimal requestedDays) {
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployee_IdAndLeaveType_IdAndBalanceYear(employee.getId(), leaveType.getId(), year)
                .orElseGet(() -> seedBalance(employee, leaveType, year));
        BigDecimal remaining = nz(balance.getRemainingDays());
        if (remaining.compareTo(requestedDays) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient leave balance: " + remaining + " day(s) remaining, requested " + requestedDays);
        }
        return balance;
    }

    private LeaveBalance seedBalance(Employee employee, LeaveType leaveType, int year) {
        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(employee);
        balance.setLeaveType(leaveType);
        balance.setBalanceYear(year);
        BigDecimal total = BigDecimal.valueOf(leaveType.getDaysAllowed());
        balance.setTotalDays(total);
        balance.setUsedDays(BigDecimal.ZERO);
        balance.setPendingDays(BigDecimal.ZERO);
        balance.setRemainingDays(total);
        balance.setUpdatedAt(LocalDateTime.now());
        return leaveBalanceRepository.save(balance);
    }

    private void releasePendingDays(LeaveRequest lr, LocalDateTime now) {
        LeaveBalance balance = findBalanceFor(lr);
        if (balance == null) return;
        BigDecimal days = nz(lr.getTotalDays());
        balance.setPendingDays(nz(balance.getPendingDays()).subtract(days).max(BigDecimal.ZERO));
        recomputeRemaining(balance);
        balance.setUpdatedAt(now);
        leaveBalanceRepository.save(balance);
    }

    private void movePendingToUsed(LeaveRequest lr, LocalDateTime now) {
        LeaveBalance balance = findBalanceFor(lr);
        if (balance == null) {
            balance = seedBalance(lr.getEmployee(), lr.getLeaveType(), lr.getStartDate().getYear());
        }
        BigDecimal days = nz(lr.getTotalDays());
        balance.setPendingDays(nz(balance.getPendingDays()).subtract(days).max(BigDecimal.ZERO));
        balance.setUsedDays(nz(balance.getUsedDays()).add(days));
        recomputeRemaining(balance);
        balance.setUpdatedAt(now);
        leaveBalanceRepository.save(balance);
    }

    private LeaveBalance findBalanceFor(LeaveRequest lr) {
        if (lr.getLeaveType() == null || lr.getStartDate() == null) return null;
        return leaveBalanceRepository
                .findByEmployee_IdAndLeaveType_IdAndBalanceYear(
                        lr.getEmployee().getId(), lr.getLeaveType().getId(), lr.getStartDate().getYear())
                .orElse(null);
    }

    private static void recomputeRemaining(LeaveBalance balance) {
        BigDecimal remaining = nz(balance.getTotalDays())
                .subtract(nz(balance.getUsedDays()))
                .subtract(nz(balance.getPendingDays()));
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
        }
        balance.setRemainingDays(remaining);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private LeaveType resolveLeaveType(String raw) {
        String q = raw.trim();
        if (q.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leaveType is required");
        }
        return leaveTypeRepository
                .findByCodeIgnoreCase(q)
                .or(() -> leaveTypeRepository.findByNameIgnoreCase(q))
                .or(() -> leaveTypeRepository.findAll().stream()
                        .filter(lt -> lt.getName().toLowerCase(Locale.US).contains(q.toLowerCase(Locale.US)))
                        .findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown leave type: " + q));
    }

    private static WorkDtos.LeaveRequestResponse toLeave(LeaveRequest lr, Employee employee) {
        String decidedBy = null;
        if (lr.getApprovedBy() != null && lr.getApprovedBy().getAppUser() != null) {
            decidedBy = lr.getApprovedBy().getAppUser().getEmail();
        }
        return new WorkDtos.LeaveRequestResponse(
                lr.getId(),
                employee.getId(),
                name(employee),
                lr.getLeaveType().getName(),
                lr.getStartDate(),
                lr.getEndDate(),
                lr.getReason(),
                lr.getStatus().toUpperCase(Locale.US),
                lr.getRejectionNote(),
                decidedBy,
                toInstant(lr.getApprovedAt()),
                toInstant(lr.getCreatedAt())
        );
    }

    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZONE).toInstant();
    }

    public List<WorkDtos.AttendanceLogResponse> myAttendance(String email) {
        Employee e = employeeForEmail(email);
        return attendanceRepository.findAllByEmployee_IdOrderByWorkDateDesc(e.getId()).stream()
                .map(this::toAttendance)
                .toList();
    }

    @Transactional
    public WorkDtos.AttendanceLogResponse upsertAttendanceForEmployee(
            UUID employeeId, WorkDtos.UpsertAttendanceLogRequest request) {
        Employee e = requireEmployeeInOrg(employeeId);
        Attendance log = attendanceRepository.findByEmployee_IdAndWorkDate(e.getId(), request.workDate())
                .orElseGet(() -> {
                    Attendance n = new Attendance();
                    n.setEmployee(e);
                    n.setWorkDate(request.workDate());
                    LocalDateTime now = LocalDateTime.now();
                    n.setCreatedAt(now);
                    return n;
                });
        log.setStatus(normalizeAttendanceStatus(request.status()));
        log.setCheckIn(combine(request.workDate(), request.checkInTime()));
        log.setCheckOut(combine(request.workDate(), request.checkOutTime()));
        recalculateWorkHours(log);
        log.setNotes(request.notes() == null ? null : request.notes().trim());
        LocalDateTime now = LocalDateTime.now();
        log.setUpdatedAt(now);
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(now);
        }
        return toAttendance(attendanceRepository.save(log));
    }

    /**
     * Mark check-in for today (server local date). Zoho People-style punch: one check-in per calendar day.
     */
    @Transactional
    public WorkDtos.AttendanceLogResponse checkInMyAttendance(String email) {
        Employee e = employeeForEmail(email);
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime now = LocalDateTime.now();
        Attendance log = attendanceRepository.findByEmployee_IdAndWorkDate(e.getId(), today).orElseGet(() -> {
            Attendance n = new Attendance();
            n.setEmployee(e);
            n.setWorkDate(today);
            n.setCreatedAt(now);
            n.setOvertimeHours(BigDecimal.ZERO);
            return n;
        });
        if (log.getCheckIn() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already checked in for today");
        }
        log.setCheckIn(now);
        log.setStatus(deriveStatusFromCheckInTime(now.toLocalTime()));
        log.setUpdatedAt(now);
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(now);
        }
        try {
            return toAttendance(attendanceRepository.save(log));
        } catch (DataIntegrityViolationException ex) {
            // Concurrent punch-in for the same day: the (employee_id, work_date) unique constraint
            // is what saved us from the duplicate; translate into a clean 409 instead of a 500.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Already checked in for today", ex);
        }
    }

    /**
     * Mark check-out for today. Requires check-in first; computes work hours.
     */
    @Transactional
    public WorkDtos.AttendanceLogResponse checkOutMyAttendance(String email) {
        Employee e = employeeForEmail(email);
        LocalDate today = LocalDate.now(ZONE);
        Attendance log = attendanceRepository.findByEmployee_IdAndWorkDate(e.getId(), today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check in before checking out"));
        if (log.getCheckIn() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check in before checking out");
        }
        if (log.getCheckOut() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already checked out for today");
        }
        LocalDateTime now = LocalDateTime.now();
        log.setCheckOut(now);
        recalculateWorkHours(log);
        log.setUpdatedAt(now);
        return toAttendance(attendanceRepository.save(log));
    }

    private static String deriveStatusFromCheckInTime(LocalTime time) {
        return time.isAfter(DEFAULT_SHIFT_START) ? "late" : "present";
    }

    private static void recalculateWorkHours(Attendance log) {
        if (log.getCheckIn() == null || log.getCheckOut() == null) {
            return;
        }
        long minutes = ChronoUnit.MINUTES.between(log.getCheckIn(), log.getCheckOut());
        if (minutes < 0) {
            minutes = 0;
        }
        log.setWorkHours(
                BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
    }

    private static String normalizeAttendanceStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "present";
        }
        String s = raw.trim().toLowerCase(Locale.US);
        if (ALLOWED_ATTENDANCE_STATUSES.contains(s)) {
            return s;
        }
        String mapped = switch (raw.trim().toUpperCase(Locale.US)) {
            case "PRESENT" -> "present";
            case "ABSENT" -> "absent";
            case "LATE" -> "late";
            case "LEAVE", "ON_LEAVE" -> "on_leave";
            case "HALF_DAY", "HALFDAY", "HALF" -> "half_day";
            case "HOLIDAY" -> "holiday";
            case "WEEKEND" -> "weekend";
            default -> null;
        };
        if (mapped != null) {
            return mapped;
        }
        // Refuse anything we can't safely store: the previous behaviour returned the raw
        // lowercased value, which then violated the DB CHECK constraint and surfaced as a 500.
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unknown attendance status: " + raw + ". Allowed: " + ALLOWED_ATTENDANCE_STATUSES);
    }

    private static LocalDateTime combine(LocalDate date, LocalTime time) {
        if (date == null || time == null) {
            return null;
        }
        return LocalDateTime.of(date, time);
    }

    public List<WorkDtos.AttendanceLogResponse> attendanceForEmployee(UUID employeeId) {
        requireEmployeeInOrg(employeeId);
        return attendanceRepository.findAllByEmployee_IdOrderByWorkDateDesc(employeeId).stream()
                .map(this::toAttendance)
                .toList();
    }

    private WorkDtos.AttendanceLogResponse toAttendance(Attendance log) {
        return new WorkDtos.AttendanceLogResponse(
                log.getId(),
                log.getEmployee().getId(),
                log.getWorkDate(),
                attendanceStatusForApi(log.getStatus()),
                log.getCheckIn() == null ? null : log.getCheckIn().toLocalTime(),
                log.getCheckOut() == null ? null : log.getCheckOut().toLocalTime(),
                log.getWorkHours(),
                log.getNotes()
        );
    }

    private static String attendanceStatusForApi(String db) {
        if (db == null) return "";
        return switch (db.toLowerCase(Locale.US)) {
            case "present" -> "PRESENT";
            case "absent" -> "ABSENT";
            case "late" -> "LATE";
            case "half_day" -> "HALF_DAY";
            case "on_leave" -> "ON_LEAVE";
            case "holiday" -> "HOLIDAY";
            case "weekend" -> "WEEKEND";
            default -> db.toUpperCase(Locale.US);
        };
    }

    public List<WorkDtos.TimeLogResponse> myTimeLogs(String email) {
        employeeForEmail(email);
        return List.of();
    }

    public WorkDtos.TimeLogResponse createMyTimeLog(String email, WorkDtos.CreateTimeLogRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Time logs are not in the current schema");
    }

    public List<WorkDtos.OnboardingTaskResponse> myOnboarding(String email) {
        employeeForEmail(email);
        return List.of();
    }

    public WorkDtos.OnboardingTaskResponse setOnboardingCompleted(String email, UUID taskId, WorkDtos.CompleteOnboardingTaskRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Onboarding tasks are not in the current schema");
    }

    public WorkDtos.ApprovalsResponse pendingApprovals() {
        return new WorkDtos.ApprovalsResponse(List.of());
    }

    public List<WorkDtos.FeedPostResponse> listFeed() {
        return List.of();
    }

    public WorkDtos.FeedPostResponse createFeedPost(String authorEmail, WorkDtos.CreateFeedPostRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Feed is not in the current schema");
    }

    public List<WorkDtos.DocumentResponse> myDocuments(String email) {
        Employee e = employeeForEmail(email);
        return hrDocumentRepository.findAllByEmployee_IdOrderByUploadedAtDesc(e.getId()).stream()
                .map(this::toDoc)
                .toList();
    }

    @Transactional
    public WorkDtos.DocumentResponse addMyDocument(String email, WorkDtos.AddDocumentRequest request) {
        Employee e = employeeForEmail(email);
        AppUser uploader = appUserRepository.findByEmail(email.trim().toLowerCase(Locale.US)).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        HrDocument d = new HrDocument();
        d.setEmployee(e);
        d.setTitle(request.name().trim());
        d.setType(normalizeDocType(request.docType()));
        d.setFileUrl(request.url().trim());
        d.setUploadedBy(uploader);
        d.setUploadedAt(now);
        return toDoc(hrDocumentRepository.save(d));
    }

    private static String normalizeDocType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "other";
        }
        String normalized = raw.trim().toLowerCase(Locale.US).replace(' ', '_').replace('-', '_');
        if (ALLOWED_DOC_TYPES.contains(normalized)) {
            return normalized;
        }
        // The DB CHECK constraint on hr_documents.type rejects anything outside the whitelist;
        // surface a clean 400 instead of letting Postgres raise a 500 check_violation.
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unknown document type: " + raw + ". Allowed: " + ALLOWED_DOC_TYPES);
    }

    @Transactional
    public void deleteMyDocument(String email, UUID docId) {
        Employee e = employeeForEmail(email);
        HrDocument d = hrDocumentRepository.findByIdAndEmployee_Id(docId, e.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        hrDocumentRepository.delete(d);
    }

    public List<WorkDtos.DocumentResponse> documentsForEmployee(UUID employeeId) {
        requireEmployeeInOrg(employeeId);
        return hrDocumentRepository.findAllByEmployee_IdOrderByUploadedAtDesc(employeeId).stream()
                .map(this::toDoc)
                .toList();
    }

    private WorkDtos.DocumentResponse toDoc(HrDocument d) {
        return new WorkDtos.DocumentResponse(
                d.getId(),
                d.getTitle(),
                d.getType(),
                d.getFileUrl(),
                d.getUploadedAt() == null ? null : d.getUploadedAt().atZone(ZONE).toInstant()
        );
    }
}
