package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.WorkDtos;
import prod.tint_wym.novora_backend.service.WorkService;

@RestController
@PreAuthorize("isAuthenticated()")
public class WorkController {

    private final WorkService workService;

    public WorkController(WorkService workService) {
        this.workService = workService;
    }

    // ---- Employee self-service (my/*) ----

    @GetMapping("/api/my/leave")
    public List<WorkDtos.LeaveRequestResponse> myLeave(Authentication auth) {
        return workService.myLeave(auth.getName());
    }

    @PostMapping("/api/my/leave")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkDtos.LeaveRequestResponse createMyLeave(Authentication auth, @Valid @RequestBody WorkDtos.CreateLeaveRequest request) {
        return workService.createMyLeave(auth.getName(), request);
    }

    @DeleteMapping("/api/my/leave/{leaveId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMyLeave(Authentication auth, @PathVariable UUID leaveId) {
        workService.cancelMyLeave(auth.getName(), leaveId);
    }

    @GetMapping("/api/my/attendance")
    public List<WorkDtos.AttendanceLogResponse> myAttendance(Authentication auth) {
        return workService.myAttendance(auth.getName());
    }

    /** Zoho People-style punch: record check-in time for today (server date). */
    @PostMapping("/api/my/attendance/check-in")
    public WorkDtos.AttendanceLogResponse checkInMyAttendance(Authentication auth) {
        return workService.checkInMyAttendance(auth.getName());
    }

    /** Zoho People-style punch: record check-out time for today; requires prior check-in. */
    @PostMapping("/api/my/attendance/check-out")
    public WorkDtos.AttendanceLogResponse checkOutMyAttendance(Authentication auth) {
        return workService.checkOutMyAttendance(auth.getName());
    }

    @GetMapping("/api/my/time-logs")
    public List<WorkDtos.TimeLogResponse> myTimeLogs(Authentication auth) {
        return workService.myTimeLogs(auth.getName());
    }

    @PostMapping("/api/my/time-logs")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkDtos.TimeLogResponse createMyTimeLog(
            Authentication auth,
            @Valid @RequestBody WorkDtos.CreateTimeLogRequest request
    ) {
        return workService.createMyTimeLog(auth.getName(), request);
    }

    @GetMapping("/api/my/onboarding")
    public List<WorkDtos.OnboardingTaskResponse> myOnboarding(Authentication auth) {
        return workService.myOnboarding(auth.getName());
    }

    @PutMapping("/api/my/onboarding/{taskId}")
    public WorkDtos.OnboardingTaskResponse updateMyOnboardingTask(
            Authentication auth,
            @PathVariable UUID taskId,
            @RequestBody WorkDtos.CompleteOnboardingTaskRequest request
    ) {
        return workService.setOnboardingCompleted(auth.getName(), taskId, request);
    }

    @GetMapping("/api/my/documents")
    public List<WorkDtos.DocumentResponse> myDocuments(Authentication auth) {
        return workService.myDocuments(auth.getName());
    }

    @PostMapping("/api/my/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkDtos.DocumentResponse addMyDocument(Authentication auth, @Valid @RequestBody WorkDtos.AddDocumentRequest request) {
        return workService.addMyDocument(auth.getName(), request);
    }

    @DeleteMapping("/api/my/documents/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyDocument(Authentication auth, @PathVariable UUID docId) {
        workService.deleteMyDocument(auth.getName(), docId);
    }

    // ---- Shared feed (authenticated) ----

    @GetMapping("/api/feeds")
    public List<WorkDtos.FeedPostResponse> listFeeds() {
        return workService.listFeed();
    }

    @PostMapping("/api/admin/feeds")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkDtos.FeedPostResponse createFeedPost(Authentication auth, @Valid @RequestBody WorkDtos.CreateFeedPostRequest request) {
        return workService.createFeedPost(auth.getName(), request);
    }

    // ---- Admin/HR operational endpoints ----

    @GetMapping("/api/admin/approvals")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public WorkDtos.ApprovalsResponse pendingApprovals() {
        return workService.pendingApprovals();
    }

    @GetMapping("/api/admin/leave/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<WorkDtos.LeaveRequestResponse> pendingLeave() {
        return workService.pendingLeave();
    }

    @PostMapping("/api/admin/leave/{leaveId}/decide")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public WorkDtos.LeaveRequestResponse decideLeave(
            Authentication auth,
            @PathVariable UUID leaveId,
            @Valid @RequestBody WorkDtos.DecideLeaveRequest request
    ) {
        return workService.decideLeave(auth.getName(), leaveId, request);
    }

    @GetMapping("/api/admin/employees/{employeeId}/attendance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<WorkDtos.AttendanceLogResponse> employeeAttendance(@PathVariable UUID employeeId) {
        return workService.attendanceForEmployee(employeeId);
    }

    @PutMapping("/api/admin/employees/{employeeId}/attendance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public WorkDtos.AttendanceLogResponse upsertEmployeeAttendance(
            @PathVariable UUID employeeId,
            @Valid @RequestBody WorkDtos.UpsertAttendanceLogRequest request
    ) {
        return workService.upsertAttendanceForEmployee(employeeId, request);
    }

    @GetMapping("/api/admin/employees/{employeeId}/documents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<WorkDtos.DocumentResponse> employeeDocuments(@PathVariable UUID employeeId) {
        return workService.documentsForEmployee(employeeId);
    }
}

