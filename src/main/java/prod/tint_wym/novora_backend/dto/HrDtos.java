package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HrDtos {

    private HrDtos() {
    }

    public record CreateDepartmentRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 20) String code,
            @Size(max = 2000) String description
    ) {
    }

    public record DepartmentResponse(
            UUID id,
            String name,
            String code,
            String description,
            boolean active
    ) {
    }

    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Email @NotBlank @Size(max = 255) String email,
            @NotNull UUID departmentId,
            UUID positionId,
            @Size(max = 20) String employeeCode,
            LocalDate hireDate
    ) {
    }

    public record UpdateEmployeeRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Email @NotBlank @Size(max = 255) String email,
            @NotNull UUID departmentId,
            @NotNull UUID positionId,
            @Size(max = 20) String employeeCode,
            LocalDate hireDate,
            @Size(max = 20) String status
    ) {
    }

    public record EmployeeResponse(
            UUID id,
            String firstName,
            String lastName,
            String email,
            UUID departmentId,
            String departmentName,
            UUID positionId,
            String positionTitle,
            UUID userId,
            List<String> accountRoles,
            String employeeCode,
            LocalDate hireDate,
            String status,
            String phone,
            String employmentType,
            String city,
            String country,
            String managerName
    ) {
    }
}
