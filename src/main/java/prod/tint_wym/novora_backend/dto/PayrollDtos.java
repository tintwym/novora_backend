package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PayrollDtos {
    private PayrollDtos() {
    }

    public record PayrollRowResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            String employeeCode,
            int payMonth,
            int payYear,
            BigDecimal basicSalary,
            BigDecimal allowances,
            BigDecimal overtimePay,
            BigDecimal bonus,
            BigDecimal deductions,
            BigDecimal tax,
            BigDecimal netPay,
            String status,
            Instant processedAt,
            Instant paidAt
    ) {
    }

    public record GeneratePayrollRequest(
            @NotNull @Min(1) @Max(12) Integer payMonth,
            @NotNull @Min(2000) @Max(2100) Integer payYear
    ) {
    }

    public record PayrollRunSummary(
            int payMonth,
            int payYear,
            int headcount,
            BigDecimal totalNetPay,
            long draftCount,
            long processedCount,
            long paidCount
    ) {
    }
}
