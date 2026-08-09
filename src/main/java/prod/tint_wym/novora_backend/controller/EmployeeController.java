package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.HrDtos;
import prod.tint_wym.novora_backend.service.EmployeeService;

@RestController
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/api/admin/employees")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<HrDtos.EmployeeResponse> listEmployees() {
        return employeeService.listEmployees();
    }

    @GetMapping("/api/admin/employees/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public HrDtos.EmployeeResponse getEmployee(@PathVariable UUID id) {
        return employeeService.getEmployee(id);
    }

    @PostMapping("/api/admin/employees")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public HrDtos.EmployeeResponse createEmployee(@Valid @RequestBody HrDtos.CreateEmployeeRequest request) {
        return employeeService.createEmployee(request);
    }

    @PutMapping("/api/admin/employees/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public HrDtos.EmployeeResponse updateEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody HrDtos.UpdateEmployeeRequest request
    ) {
        return employeeService.updateEmployee(id, request);
    }

    @DeleteMapping("/api/admin/employees/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEmployee(@PathVariable UUID id, Authentication authentication) {
        // Caller email is used by the service to block self-delete and last-SUPER_ADMIN delete.
        String callerEmail = authentication != null ? authentication.getName() : null;
        employeeService.deleteEmployee(id, callerEmail);
    }
}
