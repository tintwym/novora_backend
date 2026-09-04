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
import prod.tint_wym.novora_backend.entity.Announcement;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.entity.Attendance;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.ExpenseClaim;
import prod.tint_wym.novora_backend.entity.HrDocument;
import prod.tint_wym.novora_backend.entity.LeaveBalance;
import prod.tint_wym.novora_backend.entity.LeaveRequest;
import prod.tint_wym.novora_backend.entity.LeaveType;
import prod.tint_wym.novora_backend.entity.OnboardingTask;
import prod.tint_wym.novora_backend.entity.TimeLog;
import prod.tint_wym.novora_backend.dto.WorkDtos;
import prod.tint_wym.novora_backend.repository.AnnouncementRepository;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.repository.AttendanceRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.ExpenseClaimRepository;
import prod.tint_wym.novora_backend.repository.HrDocumentRepository;
import prod.tint_wym.novora_backend.repository.LeaveBalanceRepository;
import prod.tint_wym.novora_backend.repository.LeaveRequestRepository;
import prod.tint_wym.novora_backend.repository.LeaveTypeRepository;
import prod.tint_wym.novora_backend.repository.OnboardingTaskRepository;
import prod.tint_wym.novora_backend.repository.TimeLogRepository;
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
    private final AnnouncementRepository announcementRepository;
    private final TimeLogRepository timeLogRepository;
    private final OnboardingTaskRepository onboardingTaskRepository;
    private final ExpenseClaimRepository expenseClaimRepository;
    private final NotificationService notificationService;

    public WorkService(
            EmployeeRepository employeeRepository,
            LeaveRequestRepository leaveRequestRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            AttendanceRepository attendanceRepository,
            HrDocumentRepository hrDocumentRepository,
            AppUserRepository appUserRepository,
            AnnouncementRepository announcementRepository,
            TimeLogRepository timeLogRepository,
            OnboardingTaskRepository onboardingTaskRepository,
            ExpenseClaimRepository expenseClaimRepository,
            NotificationService notificationService
    ) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.attendanceRepository = attendanceRepository;
        this.hrDocumentRepository = hrDocumentRepository;
        this.appUserRepository = appUserRepository;
        this.announcementRepository = announcementRepository;
        this.timeLogRepository = timeLogRepository;
        this.onboardingTaskRepository = onboardingTaskRepository;
        this.expenseClaimRepository = expenseClaimRepository;
        this.notificationService = notificationService;
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
    public List<WorkDtos.LeaveBalanceResponse> myLeaveBalances(String email) {
        Employee e = employeeForEmail(email);
        int year = LocalDate.now(ZONE).getYear();
        List<LeaveBalance> balances = leaveBalanceRepository.findAllByEmployee_IdAndBalanceYear(e.getId(), year);
        if (balances.isEmpty()) {
            // Seed on first read so existing employees created before balance seeding still work.
            for (LeaveType leaveType : leaveTypeRepository.findAll()) {
                if (!leaveType.isActive()) continue;
                LeaveBalance seeded = seedBalance(e, leaveType, year);
                balances = new java.util.ArrayList<>(balances);
                balances.add(seeded);
            }
        }
        return balances.stream()
                .map(b -> new WorkDtos.LeaveBalanceResponse(
                        b.getId(),
                        b.getLeaveType().getName(),
                        b.getLeaveType().getCode(),
                        b.getBalanceYear(),
                        nz(b.getTotalDays()),
                        nz(b.getUsedDays()),
                        nz(b.getPendingDays()),
                        nz(b.getRemainingDays())))
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
        try {
            AppUser targetUser = saved.getEmployee().getAppUser();
            if (targetUser != null) {
                String decisionLabel = "APPROVE".equals(decision) ? "approved" : "rejected";
                notificationService.createNotification(
                        targetUser.getId(),
                        "Leave request " + decisionLabel,
                        "Your leave request from " + saved.getStartDate() + " to " + saved.getEndDate()
                                + " was " + decisionLabel + ".",
                        "leave");
            }
        } catch (Exception ignored) {
            // best-effort
        }
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
        Employee e = employeeForEmail(email);
        return timeLogRepository.findAllByEmployee_IdOrderByWorkDateDescCreatedAtDesc(e.getId()).stream()
                .map(this::toTimeLog)
                .toList();
    }

    @Transactional
    public WorkDtos.TimeLogResponse createMyTimeLog(String email, WorkDtos.CreateTimeLogRequest request) {
        Employee e = employeeForEmail(email);
        UUID orgId = requireOrganizationId();
        if (request.hours() == null || request.hours().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hours must be zero or positive");
        }
        TimeLog log = new TimeLog();
        log.setOrganizationId(orgId);
        log.setEmployee(e);
        log.setWorkDate(request.workDate());
        log.setHours(request.hours());
        log.setProject(request.project() == null || request.project().isBlank() ? null : request.project().trim());
        log.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        log.setCreatedAt(LocalDateTime.now());
        return toTimeLog(timeLogRepository.save(log));
    }

    private WorkDtos.TimeLogResponse toTimeLog(TimeLog log) {
        return new WorkDtos.TimeLogResponse(
                log.getId(),
                log.getEmployee().getId(),
                log.getWorkDate(),
                log.getHours(),
                log.getProject(),
                log.getNotes(),
                toInstant(log.getCreatedAt()));
    }

    public List<WorkDtos.OnboardingTaskResponse> myOnboarding(String email) {
        Employee e = employeeForEmail(email);
        return onboardingTaskRepository.findAllByEmployee_IdOrderBySortOrderAscCreatedAtAsc(e.getId()).stream()
                .map(OpsService::toMyOnboarding)
                .toList();
    }

    @Transactional
    public WorkDtos.OnboardingTaskResponse setOnboardingCompleted(
            String email, UUID taskId, WorkDtos.CompleteOnboardingTaskRequest request) {
        Employee e = employeeForEmail(email);
        OnboardingTask task = onboardingTaskRepository
                .findByIdAndEmployee_Id(taskId, e.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Onboarding task not found"));
        boolean completed = request == null || request.completed();
        if (completed) {
            task.setStatus("completed");
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setStatus("pending");
            task.setCompletedAt(null);
        }
        return OpsService.toMyOnboarding(onboardingTaskRepository.save(task));
    }

    public WorkDtos.ApprovalsResponse pendingApprovals() {
        UUID orgId = requireOrganizationId();
        java.util.ArrayList<WorkDtos.ApprovalTaskResponse> tasks = new java.util.ArrayList<>();
        for (LeaveRequest lr :
                leaveRequestRepository.findAllByStatusAndEmployee_OrganizationIdOrderByCreatedAtDesc("pending", orgId)) {
            Employee emp = lr.getEmployee();
            tasks.add(new WorkDtos.ApprovalTaskResponse(
                    lr.getId(),
                    "LEAVE",
                    lr.getId(),
                    "PENDING",
                    emp.getId(),
                    name(emp),
                    toInstant(lr.getCreatedAt())));
        }
        for (ExpenseClaim claim : expenseClaimRepository.findAllByStatusIgnoreCaseOrderByCreatedAtDesc("pending")) {
            Employee emp = claim.getEmployee();
            tasks.add(new WorkDtos.ApprovalTaskResponse(
                    claim.getId(),
                    "CLAIM",
                    claim.getId(),
                    "PENDING",
                    emp.getId(),
                    name(emp),
                    toInstant(claim.getCreatedAt())));
        }
        return new WorkDtos.ApprovalsResponse(tasks);
    }

    public List<WorkDtos.FeedPostResponse> listFeed() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toFeed)
                .toList();
    }

    @Transactional
    public WorkDtos.FeedPostResponse createFeedPost(String authorEmail, WorkDtos.CreateFeedPostRequest request) {
        UUID orgId = requireOrganizationId();
        AppUser author = appUserRepository
                .findByEmail(authorEmail.trim().toLowerCase(Locale.US))
                .orElse(null);
        Employee authorEmployee = employeeRepository
                .findByAppUser_EmailIgnoreCase(authorEmail.trim().toLowerCase(Locale.US))
                .orElse(null);
        LocalDateTime now = LocalDateTime.now();
        Announcement a = new Announcement();
        a.setOrganizationId(orgId);
        a.setTitle(request.title().trim());
        a.setContent(request.body().trim());
        a.setType("general");
        a.setTargetRole("ALL");
        a.setPinned(false);
        a.setPublishedAt(now);
        a.setCreatedBy(author);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        Announcement saved = announcementRepository.save(a);
        return new WorkDtos.FeedPostResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getContent(),
                authorEmployee != null ? authorEmployee.getId() : null,
                authorEmployee != null ? name(authorEmployee) : (author != null ? author.getEmail() : null),
                toInstant(saved.getCreatedAt()));
    }

    private WorkDtos.FeedPostResponse toFeed(Announcement a) {
        UUID authorEmployeeId = null;
        String authorName = null;
        if (a.getCreatedBy() != null) {
            authorName = a.getCreatedBy().getEmail();
            Employee emp = employeeRepository
                    .findByAppUser_EmailIgnoreCase(a.getCreatedBy().getEmail())
                    .orElse(null);
            if (emp != null) {
                authorEmployeeId = emp.getId();
                authorName = name(emp);
            }
        }
        return new WorkDtos.FeedPostResponse(
                a.getId(),
                a.getTitle(),
                a.getContent(),
                authorEmployeeId,
                authorName,
                toInstant(a.getCreatedAt()));
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
        String contentBase64 =
                request.contentBase64() == null || request.contentBase64().isBlank()
                        ? null
                        : request.contentBase64().trim();
        String url = request.url() == null || request.url().isBlank() ? null : request.url().trim();
        if (contentBase64 == null && url == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Either url or contentBase64 is required");
        }
        if (url != null && !isAllowedDocumentUrl(url)) {
            // When inline content is present, allow a placeholder/skipped URL; otherwise require
            // https:// or novora://.
            if (contentBase64 == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Document URL must start with https:// or novora://");
            }
            url = null;
        }
        LocalDateTime now = LocalDateTime.now();
        HrDocument d = new HrDocument();
        d.setOrganizationId(requireOrganizationId());
        d.setEmployee(e);
        d.setTitle(request.name().trim());
        d.setType(normalizeDocType(request.docType()));
        d.setContentBase64(contentBase64);
        d.setFileUrl(url != null ? url : "pending");
        d.setUploadedBy(uploader);
        d.setUploadedAt(now);
        HrDocument saved = hrDocumentRepository.save(d);
        if (contentBase64 != null && (url == null || url.equals("pending"))) {
            saved.setFileUrl("novora://documents/" + saved.getId());
            saved = hrDocumentRepository.save(saved);
        }
        return toDoc(saved);
    }

    private static boolean isAllowedDocumentUrl(String url) {
        String u = url.trim();
        return u.matches("^https://[\\w.\\-]+(:\\d+)?(/[^\\s]*)?$")
                || u.matches("^novora://documents/[0-9a-fA-F\\-]{36}$");
    }

    public WorkDtos.DocumentContentResponse myDocumentContent(String email, UUID docId) {
        Employee e = employeeForEmail(email);
        HrDocument d = hrDocumentRepository
                .findByIdAndEmployee_Id(docId, e.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return new WorkDtos.DocumentContentResponse(d.getTitle(), d.getContentBase64(), d.getType());
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
