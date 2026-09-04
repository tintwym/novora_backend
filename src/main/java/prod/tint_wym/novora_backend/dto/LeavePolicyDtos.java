package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class LeavePolicyDtos {
    private LeavePolicyDtos() {
    }

    public record LeaveTypeResponse(
            UUID id,
            String name,
            String code,
            int daysAllowed,
            boolean paid,
            boolean carryForward,
            int maxCarryDays,
            String description,
            boolean active,
            Instant createdAt
    ) {
    }

    public record CreateLeaveTypeRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 30) String code,
            Integer daysAllowed,
            Boolean paid,
            Boolean carryForward,
            Integer maxCarryDays,
            @Size(max = 2000) String description,
            Boolean active
    ) {
    }

    public record UpdateLeaveTypeRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 30) String code,
            Integer daysAllowed,
            Boolean paid,
            Boolean carryForward,
            Integer maxCarryDays,
            @Size(max = 2000) String description,
            Boolean active
    ) {
    }

    public record HolidayResponse(
            UUID id,
            String name,
            LocalDate holidayDate,
            String type,
            String description,
            Instant createdAt
    ) {
    }

    public record CreateHolidayRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate holidayDate,
            @Size(max = 20) String type,
            @Size(max = 2000) String description
    ) {
    }
}
