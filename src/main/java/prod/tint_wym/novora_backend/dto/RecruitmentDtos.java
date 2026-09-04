package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class RecruitmentDtos {
    private RecruitmentDtos() {
    }

    public record JobPostingResponse(
            UUID id,
            String title,
            String departmentName,
            String location,
            String employmentType,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            LocalDate openDate,
            LocalDate closeDate,
            Integer openings,
            boolean published,
            String status,
            long applicantCount,
            Instant createdAt
    ) {
    }

    public record CreateJobPostingRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 100) String departmentName,
            @Size(max = 100) String location,
            @Size(max = 20) String employmentType,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            LocalDate openDate,
            LocalDate closeDate,
            Integer openings,
            @Size(max = 5000) String description,
            boolean publish
    ) {
    }

    public record UpdateJobStatusRequest(
            @NotBlank String status
    ) {
    }

    public record CandidateResponse(
            UUID id,
            UUID jobPostingId,
            String jobTitle,
            String fullName,
            String email,
            String phone,
            String source,
            String stage,
            String status,
            Integer rating,
            String notes,
            Instant appliedAt
    ) {
    }

    public record CreateCandidateRequest(
            UUID jobPostingId,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Size(max = 255) String email,
            @Size(max = 20) String phone,
            @Size(max = 50) String source,
            @Size(max = 2000) String notes
    ) {
    }

    public record UpdateCandidateStageRequest(
            @NotBlank String stage,
            String status
    ) {
    }

    public record InterviewResponse(
            UUID id,
            UUID candidateId,
            String candidateName,
            UUID interviewerEmployeeId,
            String interviewerName,
            Instant scheduledAt,
            Integer durationMins,
            String mode,
            String location,
            String round,
            String status,
            Instant createdAt
    ) {
    }

    public record CreateInterviewRequest(
            @NotNull UUID candidateId,
            UUID interviewerEmployeeId,
            @NotNull Instant scheduledAt,
            Integer durationMins,
            @Size(max = 20) String mode,
            @Size(max = 200) String location,
            @Size(max = 30) String round
    ) {
    }

    public record JobOfferResponse(
            UUID id,
            UUID candidateId,
            String candidateName,
            BigDecimal salary,
            String currency,
            BigDecimal allowance,
            String grade,
            String probation,
            String status,
            Instant sentAt,
            LocalDate expiryDate,
            String notes,
            Instant createdAt
    ) {
    }

    public record CreateJobOfferRequest(
            @NotNull UUID candidateId,
            BigDecimal salary,
            BigDecimal allowance,
            @Size(max = 50) String grade,
            @Size(max = 100) String probation,
            @Size(max = 20) String status,
            LocalDate expiryDate,
            @Size(max = 2000) String notes
    ) {
    }

    public record UpdateOfferStatusRequest(
            @NotBlank String status
    ) {
    }
}
