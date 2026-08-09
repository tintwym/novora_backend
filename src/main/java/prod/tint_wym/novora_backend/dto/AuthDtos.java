package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * Password rules: 8–72 characters (BCrypt limit), at least one lowercase, one uppercase,
     * one digit, and one symbol (non-alphanumeric).
     */
    public static final String PASSWORD_PATTERN =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$";

    public record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email,
            @NotBlank(message = "Password is required")
            @Pattern(
                    regexp = PASSWORD_PATTERN,
                    message =
                            "Password must be 8–72 characters and include uppercase, lowercase, a number, and a symbol")
            String password,
            // Required for workspace naming when no Admin org exists yet; ignored when joining an Admin workspace.
            @NotBlank(message = "Company name is required")
            @Size(min = 2, max = 120, message = "Company name must be 2–120 characters")
            String companyName,
            @Size(max = 200, message = "Name must be at most 200 characters")
            String fullName
    ) {
    }

    /**
     * Workspace provisioning after Firebase Auth sign-up on the client. Email and Firebase UID come
     * from the verified Bearer token — not from the request body.
     */
    public record FirebaseRegisterRequest(
            @NotBlank(message = "Company name is required")
            @Size(min = 2, max = 120, message = "Company name must be 2–120 characters")
            String companyName,
            @Size(max = 200, message = "Name must be at most 200 characters")
            String fullName
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email,
            @NotBlank(message = "Password is required")
            String password
    ) {
    }

    /**
     * Snapshot of the caller's workspace, returned alongside identity on every auth response so
     * the SPA can render the trial countdown / upgrade banner without an extra round-trip.
     *
     * @param plan TRIAL / PAID / ENTERPRISE / EXPIRED
     * @param status ACTIVE / READ_ONLY / SUSPENDED — drives whether writes are allowed
     * @param trialExpiresAt when the trial flips to READ_ONLY (null for non-TRIAL)
     */
    public record OrganizationSnapshot(
            UUID id,
            String name,
            String slug,
            String plan,
            String status,
            LocalDateTime trialExpiresAt
    ) {
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            UUID userId,
            String email,
            /** Display name from the linked employee profile when available. */
            String fullName,
            List<String> roles,
            OrganizationSnapshot organization
    ) {
    }
}