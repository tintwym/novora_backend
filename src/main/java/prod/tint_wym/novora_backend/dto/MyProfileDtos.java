package prod.tint_wym.novora_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class MyProfileDtos {

    public record MyProfileResponse(
            UUID employeeId,
            String firstName,
            String lastName,
            String email,
            String departmentName,
            String jobTitle,
            UUID managerEmployeeId,
            String managerName,
            LocalDate dateOfBirth,
            Personal personal
    ) {
    }

    public record Personal(
            String phone,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country,
            String emergencyContactName,
            String emergencyContactPhone
    ) {
    }

    public record UpdateMyProfileRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            LocalDate dateOfBirth,
            @Size(max = 128) String jobTitle,
            Personal personal
    ) {
    }

    /** Employee self-service: personal contact fields only (requires OTP). */
    public record UpdatePersonalRequest(
            @NotBlank @Size(min = 6, max = 6) String otpCode,
            @jakarta.validation.constraints.NotNull @Valid Personal personal
    ) {
    }

    public record RequestPersonalOtpResponse(
            long expiresInSeconds,
            String message,
            /** Present only when {@code app.otp.expose-code=true} (local/dev). */
            String debugCode
    ) {
    }

    public record FamilyResponse(
            UUID id,
            String name,
            String relationship,
            LocalDate dateOfBirth,
            String phone
    ) {
    }

    public record CreateFamilyRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String relationship,
            LocalDate dateOfBirth,
            @Size(max = 40) String phone
    ) {
    }

    public record UpdateFamilyRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String relationship,
            LocalDate dateOfBirth,
            @Size(max = 40) String phone
    ) {
    }

    public record EducationResponse(
            UUID id,
            String institution,
            String degree,
            String fieldOfStudy,
            LocalDate startDate,
            LocalDate endDate,
            String grade
    ) {
    }

    public record CreateEducationRequest(
            @NotBlank @Size(max = 200) String institution,
            @Size(max = 160) String degree,
            @Size(max = 160) String fieldOfStudy,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 64) String grade
    ) {
    }

    public record UpdateEducationRequest(
            @NotBlank @Size(max = 200) String institution,
            @Size(max = 160) String degree,
            @Size(max = 160) String fieldOfStudy,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 64) String grade
    ) {
    }

    public record OrgNode(
            UUID employeeId,
            String name,
            String jobTitle,
            String departmentName,
            UUID managerEmployeeId
    ) {
    }

    public record OrgChartResponse(List<OrgNode> nodes) {
    }
}

