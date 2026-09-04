package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ClaimDtos {
    private ClaimDtos() {
    }

    public record ClaimResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            String departmentName,
            String category,
            LocalDate claimDate,
            BigDecimal amount,
            String currency,
            String vendor,
            String description,
            String status,
            String decisionNote,
            String decidedBy,
            Instant decidedAt,
            Instant createdAt
    ) {
    }

    public record CreateClaimRequest(
            @NotBlank @Size(max = 80) String category,
            @NotNull LocalDate claimDate,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 3) String currency,
            @Size(max = 200) String vendor,
            @Size(max = 1000) String description
    ) {
    }

    public record DecideClaimRequest(
            @NotBlank String decision,
            @Size(max = 500) String note
    ) {
    }
}
