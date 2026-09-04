package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OpsDtos {
    private OpsDtos() {
    }

    // ---- Helpdesk ----

    public record HelpdeskTicketResponse(
            UUID id,
            String subject,
            String description,
            String category,
            String priority,
            String status,
            UUID requesterEmployeeId,
            String requesterName,
            UUID assigneeEmployeeId,
            String assigneeName,
            Instant createdAt,
            Instant updatedAt,
            List<HelpdeskReplyResponse> replies
    ) {
    }

    public record HelpdeskReplyResponse(
            UUID id,
            UUID authorEmployeeId,
            String authorName,
            String body,
            Instant createdAt
    ) {
    }

    public record CreateHelpdeskTicketRequest(
            @NotBlank @Size(max = 255) String subject,
            String description,
            @Size(max = 80) String category,
            @Size(max = 40) String priority,
            UUID requesterEmployeeId,
            UUID assigneeEmployeeId,
            @Size(max = 40) String status
    ) {
    }

    public record CreateHelpdeskReplyRequest(
            @NotBlank String body
    ) {
    }

    // ---- Disciplinary ----

    public record DisciplinaryCaseResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            String reason,
            String actionType,
            String severity,
            String status,
            String notes,
            LocalDate incidentDate,
            Instant createdAt
    ) {
    }

    public record CreateDisciplinaryCaseRequest(
            @NotNull UUID employeeId,
            @NotBlank @Size(max = 255) String reason,
            @Size(max = 80) String actionType,
            @Size(max = 40) String severity,
            @Size(max = 40) String status,
            String notes,
            LocalDate incidentDate
    ) {
    }

    // ---- Benefits ----

    public record BenefitPlanResponse(
            UUID id,
            String name,
            String category,
            String provider,
            String coverageSummary,
            BigDecimal employeeCost,
            BigDecimal employerCost,
            String status,
            Instant createdAt
    ) {
    }

    public record CreateBenefitPlanRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 80) String category,
            @Size(max = 160) String provider,
            String coverageSummary,
            BigDecimal employeeCost,
            BigDecimal employerCost,
            @Size(max = 40) String status
    ) {
    }

    public record BenefitEnrollmentResponse(
            UUID id,
            UUID planId,
            String planName,
            UUID employeeId,
            String employeeName,
            String status,
            Instant enrolledAt,
            String notes
    ) {
    }

    public record CreateBenefitEnrollmentRequest(
            @NotNull UUID planId,
            @NotNull UUID employeeId,
            @Size(max = 40) String status,
            String notes
    ) {
    }

    // ---- Onboarding (admin) ----

    public record AdminOnboardingTaskResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            String title,
            String description,
            LocalDate dueDate,
            String status,
            int sortOrder,
            Instant completedAt,
            Instant createdAt
    ) {
    }

    public record CreateOnboardingTaskRequest(
            @NotNull UUID employeeId,
            @NotBlank @Size(max = 200) String title,
            String description,
            LocalDate dueDate,
            Integer sortOrder
    ) {
    }

    // ---- Training enrollments ----

    public record TrainingEnrollmentResponse(
            UUID id,
            UUID trainingId,
            String trainingTitle,
            UUID employeeId,
            String employeeName,
            String status,
            Instant enrolledAt,
            Instant completedAt,
            BigDecimal score
    ) {
    }

    public record CreateTrainingEnrollmentRequest(
            @NotNull UUID employeeId
    ) {
    }

    public record CompleteTrainingEnrollmentRequest(
            BigDecimal score,
            String feedback
    ) {
    }
}
