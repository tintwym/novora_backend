package prod.tint_wym.novora_backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class CrmIntegrationDtos {

    private CrmIntegrationDtos() {
    }

    /** What the HR admin sees on the integrations screen. */
    public record ConnectionStatus(
            boolean available,
            boolean connected,
            String crmCompanyId,
            LocalDateTime connectedAt,
            long pendingEvents
    ) {
    }

    public record LinkCodeResponse(
            String code,
            LocalDateTime expiresAt
    ) {
    }

    public record RedeemRequest(
            String code,
            String crmCompanyId
    ) {
    }

    public record RedeemResponse(
            UUID organizationId,
            String organizationName
    ) {
    }

    /**
     * Everything Novora CRM needs to decide whether a person gets an account and with which role.
     * {@code email} is null for terminated employees, whose stored address has been replaced by a
     * tombstone value.
     */
    public record EmployeeSnapshot(
            UUID employeeId,
            UUID organizationId,
            String firstName,
            String lastName,
            String email,
            String employeeCode,
            String status,
            UUID departmentId,
            String departmentCode,
            String departmentName,
            String positionTitle,
            String positionLevel,
            UUID managerEmployeeId,
            UUID departmentManagerEmployeeId,
            LocalDate hireDate,
            LocalDate endDate,
            LocalDateTime updatedAt
    ) {
    }

    public record EmployeeListResponse(
            UUID organizationId,
            LocalDateTime generatedAt,
            List<EmployeeSnapshot> employees
    ) {
    }
}
