package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record UserSummary(
            UUID userId,
            String email,
            List<String> roles,
            boolean active
    ) {
    }

    public record UpdateUserRolesRequest(
            @NotEmpty List<@NotBlank @Size(max = 64) String> roles
    ) {
    }

    /** Sets an initial password and activates a provisioned (inactive) login. */
    public record ActivateUserRequest(
            @NotBlank(message = "Password is required")
            @Pattern(
                    regexp = AuthDtos.PASSWORD_PATTERN,
                    message =
                            "Password must be 8–72 characters and include uppercase, lowercase, a number, and a symbol")
            String password
    ) {
    }

    public record ActivateUserResponse(
            UUID userId,
            String email,
            boolean active
    ) {
    }

    public record CreateRoleRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 255) String description
    ) {
    }
}

