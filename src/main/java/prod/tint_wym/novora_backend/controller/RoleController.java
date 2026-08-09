package prod.tint_wym.novora_backend.controller;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.service.RoleService;

@RestController
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/api/admin/roles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public List<String> listRoles() {
        return roleService.listRoles();
    }
}
