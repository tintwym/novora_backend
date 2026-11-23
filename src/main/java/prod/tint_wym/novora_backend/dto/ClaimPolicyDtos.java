package prod.tint_wym.novora_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class ClaimPolicyDtos {
    private ClaimPolicyDtos() {
    }

    public record CategoryLimitResponse(
            UUID id,
            String category,
            BigDecimal dailyLimit,
            BigDecimal monthlyLimit,
            String receiptRequired
    ) {
    }

    public record ClaimPolicyResponse(
            BigDecimal receiptRequiredAbove,
            int maxClaimAgeDays,
            boolean blockFutureDates,
            boolean enforceReceipt,
            boolean enforceClaimAge,
            List<CategoryLimitResponse> categoryLimits
    ) {
    }

    public record CategoryLimitRequest(
            @NotBlank @Size(max = 80) String category,
            @DecimalMin("0.00") BigDecimal dailyLimit,
            @DecimalMin("0.00") BigDecimal monthlyLimit,
            @NotBlank @Size(max = 20) String receiptRequired
    ) {
    }

    public record UpdateClaimPolicyRequest(
            @NotNull @DecimalMin("0.00") BigDecimal receiptRequiredAbove,
            @NotNull @Min(1) Integer maxClaimAgeDays,
            @NotNull Boolean blockFutureDates,
            @NotNull Boolean enforceReceipt,
            @NotNull Boolean enforceClaimAge,
            @Valid List<CategoryLimitRequest> categoryLimits
    ) {
    }
}
