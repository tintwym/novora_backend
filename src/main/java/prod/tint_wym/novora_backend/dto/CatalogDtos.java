package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record AllowanceTypeResponse(
            UUID id,
            String name,
            String code,
            BigDecimal amount,
            String frequency,
            boolean taxable,
            boolean active,
            String description,
            Instant createdAt
    ) {
    }

    public record CreateAllowanceTypeRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String code,
            @NotNull BigDecimal amount,
            @Size(max = 20) String frequency,
            Boolean taxable,
            Boolean active,
            @Size(max = 2000) String description
    ) {
    }

    public record ShiftPatternResponse(
            UUID id,
            String name,
            LocalTime startTime,
            LocalTime endTime,
            int breakMins,
            String color,
            boolean active,
            Instant createdAt
    ) {
    }

    public record CreateShiftPatternRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            Integer breakMins,
            @Size(max = 20) String color,
            Boolean active
    ) {
    }

    public record RosterEntryResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            LocalDate workDate,
            UUID shiftPatternId,
            String shiftPatternName,
            String status,
            String notes,
            Instant createdAt
    ) {
    }

    public record CreateRosterEntryRequest(
            @NotNull UUID employeeId,
            @NotNull LocalDate workDate,
            UUID shiftPatternId,
            @Size(max = 30) String status,
            @Size(max = 2000) String notes
    ) {
    }

    public record PositionResponse(
            UUID id,
            String title,
            UUID departmentId,
            String departmentName,
            String level,
            BigDecimal minSalary,
            BigDecimal maxSalary,
            boolean active,
            Instant createdAt
    ) {
    }

    public record CreatePositionRequest(
            @NotBlank @Size(max = 100) String title,
            UUID departmentId,
            @Size(max = 100) String departmentName,
            @Size(max = 20) String level,
            BigDecimal minSalary,
            BigDecimal maxSalary,
            Boolean active
    ) {
    }

    public record OrganizationProfileResponse(
            UUID id,
            String name,
            String slug,
            String legalName,
            String registrationNo,
            String addressLine1,
            String city,
            String country,
            String phone,
            String website
    ) {
    }

    public record UpdateOrganizationProfileRequest(
            @Size(max = 120) String name,
            @Size(max = 200) String legalName,
            @Size(max = 80) String registrationNo,
            String addressLine1,
            @Size(max = 100) String city,
            @Size(max = 100) String country,
            @Size(max = 40) String phone,
            @Size(max = 255) String website
    ) {
    }

    public record BranchResponse(
            UUID id,
            String name,
            String city,
            String address,
            int headcount,
            boolean active,
            Instant createdAt
    ) {
    }

    public record CreateBranchRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 100) String city,
            String address,
            Integer headcount,
            Boolean active
    ) {
    }

    public record UpdateBranchRequest(
            @Size(max = 120) String name,
            @Size(max = 100) String city,
            String address,
            Integer headcount,
            Boolean active
    ) {
    }

    public record AssetResponse(
            UUID id,
            String name,
            String assetCode,
            String category,
            String brand,
            String model,
            String serialNumber,
            LocalDate purchaseDate,
            BigDecimal purchasePrice,
            UUID assignedToId,
            String assignedToName,
            String assetCondition,
            String location,
            String notes,
            Instant createdAt
    ) {
    }

    public record CreateAssetRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 50) String assetCode,
            @Size(max = 50) String category,
            @Size(max = 100) String brand,
            @Size(max = 100) String model,
            @Size(max = 100) String serialNumber,
            LocalDate purchaseDate,
            BigDecimal purchasePrice,
            UUID assignedToId,
            @Size(max = 20) String assetCondition,
            @Size(max = 100) String location,
            @Size(max = 2000) String notes
    ) {
    }

    public record TrainingResponse(
            UUID id,
            String title,
            String description,
            String category,
            String trainer,
            String location,
            String mode,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal durationHours,
            Integer maxParticipants,
            BigDecimal cost,
            String status,
            Instant createdAt
    ) {
    }

    public record CreateTrainingRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @Size(max = 100) String category,
            @Size(max = 100) String trainer,
            @Size(max = 200) String location,
            @Size(max = 20) String mode,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal durationHours,
            Integer maxParticipants,
            BigDecimal cost,
            @Size(max = 20) String status
    ) {
    }

    public record PerformanceReviewResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            UUID reviewerId,
            String reviewerName,
            int reviewYear,
            Integer reviewQuarter,
            String reviewType,
            BigDecimal score,
            String rating,
            String status,
            Instant createdAt
    ) {
    }

    public record CreatePerformanceReviewRequest(
            @NotNull UUID employeeId,
            UUID reviewerId,
            @NotNull Integer reviewYear,
            Integer reviewQuarter,
            @Size(max = 20) String reviewType,
            BigDecimal score,
            @Size(max = 20) String rating,
            @Size(max = 5000) String goals,
            @Size(max = 5000) String comments,
            @Size(max = 20) String status
    ) {
    }

    public record AuditLogResponse(
            UUID id,
            String action,
            String tableName,
            UUID recordId,
            String userEmail,
            Instant createdAt
    ) {
    }

    public record ReportSummaryResponse(
            long employees,
            long pendingLeave,
            long openJobs,
            long candidates,
            long claimsPending,
            long payrollHeadcountThisMonth
    ) {
    }
}
