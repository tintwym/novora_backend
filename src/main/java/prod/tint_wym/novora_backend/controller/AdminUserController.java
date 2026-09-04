package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.AdminDtos;
import prod.tint_wym.novora_backend.service.AdminUserService;

@RestController
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/api/admin/users")
    public List<AdminDtos.UserSummary> listUsers() {
        return adminUserService.listUsers();
    }

    @PutMapping("/api/admin/users/{id}/roles")
    @ResponseStatus(HttpStatus.OK)
    public AdminDtos.UserSummary setUserRoles(
            @PathVariable UUID id,
            @Valid @RequestBody AdminDtos.UpdateUserRolesRequest request,
            Authentication authentication
    ) {
        String callerEmail = authentication == null ? null : authentication.getName();
        return adminUserService.setUserRoles(id, request, callerEmail);
    }

    @PostMapping("/api/admin/users/{id}/activate")
    @ResponseStatus(HttpStatus.OK)
    public AdminDtos.ActivateUserResponse activateUser(
            @PathVariable UUID id,
            @Valid @RequestBody AdminDtos.ActivateUserRequest request,
            Authentication authentication
    ) {
        String callerEmail = authentication == null ? null : authentication.getName();
        return adminUserService.activateUser(id, request, callerEmail);
    }

    @PostMapping("/api/admin/users/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDtos.UserSummary inviteUser(
            @Valid @RequestBody AdminDtos.InviteUserRequest request, Authentication authentication) {
        String callerEmail = authentication == null ? null : authentication.getName();
        return adminUserService.inviteUser(request, callerEmail);
    }

    @PostMapping("/api/admin/users/{id}/deactivate")
    @ResponseStatus(HttpStatus.OK)
    public AdminDtos.UserSummary deactivateUser(@PathVariable UUID id, Authentication authentication) {
        String callerEmail = authentication == null ? null : authentication.getName();
        return adminUserService.deactivateUser(id, callerEmail);
    }
}
