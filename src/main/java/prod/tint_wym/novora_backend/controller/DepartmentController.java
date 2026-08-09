package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.HrDtos;
import prod.tint_wym.novora_backend.service.DepartmentService;

@RestController
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/api/admin/departments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<HrDtos.DepartmentResponse> listDepartments() {
        return departmentService.listDepartments();
    }

    @PostMapping("/api/admin/departments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public HrDtos.DepartmentResponse createDepartment(@Valid @RequestBody HrDtos.CreateDepartmentRequest request) {
        return departmentService.createDepartment(request);
    }
}
