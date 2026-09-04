package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class WorkDtos {
    private WorkDtos() {
    }

    // Leave
    public record LeaveRequestResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            String status,
            String decisionNote,
            String decidedBy,
            Instant decidedAt,
            Instant createdAt
    ) {
    }

    public record CreateLeaveRequest(
            @NotBlank @Size(max = 80) String leaveType,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500) String reason
    ) {
    }

    public record DecideLeaveRequest(
            @NotBlank String decision, // APPROVE|REJECT
            @Size(max = 500) String note
    ) {
    }

    public record LeaveBalanceResponse(
            UUID id,
            String leaveType,
            String leaveTypeCode,
            int balanceYear,
            BigDecimal totalDays,
            BigDecimal usedDays,
            BigDecimal pendingDays,
            BigDecimal remainingDays
    ) {
    }

    // Attendance
    public record AttendanceLogResponse(
            UUID id,
            UUID employeeId,
            LocalDate workDate,
            String status,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            BigDecimal workHours,
            String notes
    ) {
    }

    public record UpsertAttendanceLogRequest(
            @NotNull LocalDate workDate,
            @NotBlank @Size(max = 24) String status,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            @Size(max = 500) String notes
    ) {
    }

    // Time logs
    public record TimeLogResponse(
            UUID id,
            UUID employeeId,
            LocalDate workDate,
            BigDecimal hours,
            String project,
            String notes,
            Instant createdAt
    ) {
    }

    public record CreateTimeLogRequest(
            @NotNull LocalDate workDate,
            @NotNull BigDecimal hours,
            @Size(max = 160) String project,
            @Size(max = 2000) String notes
    ) {
    }

    // Onboarding
    public record OnboardingTaskResponse(
            UUID id,
            String title,
            LocalDate dueDate,
            boolean completed
    ) {
    }

    public record CompleteOnboardingTaskRequest(
            boolean completed
    ) {
    }

    // Approvals (admin/hr)
    public record ApprovalTaskResponse(
            UUID id,
            String kind,
            UUID entityId,
            String status,
            UUID requestedByEmployeeId,
            String requestedByName,
            Instant createdAt
    ) {
    }

    public record ApprovalsResponse(List<ApprovalTaskResponse> tasks) {
    }

    // Feeds
    public record FeedPostResponse(
            UUID id,
            String title,
            String body,
            UUID authorEmployeeId,
            String authorName,
            Instant createdAt
    ) {
    }

    public record CreateFeedPostRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String body
    ) {
    }

    // Documents (metadata + optional inline base64 content)
    public record DocumentResponse(
            UUID id,
            String name,
            String docType,
            String url,
            Instant uploadedAt
    ) {
    }

    public record DocumentContentResponse(
            String name,
            String contentBase64,
            String docType
    ) {
    }

    public record AddDocumentRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 80) String docType,
            // Optional when contentBase64 is provided. https:// or novora://documents/{id}.
            @Size(max = 500) String url,
            String contentBase64
    ) {
    }
}

