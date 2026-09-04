package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.LeavePolicyDtos;
import prod.tint_wym.novora_backend.service.LeavePolicyService;

@RestController
@PreAuthorize("isAuthenticated()")
public class LeavePolicyController {

    private final LeavePolicyService leavePolicyService;

    public LeavePolicyController(LeavePolicyService leavePolicyService) {
        this.leavePolicyService = leavePolicyService;
    }

    @GetMapping("/api/admin/leave-types")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<LeavePolicyDtos.LeaveTypeResponse> adminListLeaveTypes() {
        return leavePolicyService.listLeaveTypes(false);
    }

    @PostMapping("/api/admin/leave-types")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public LeavePolicyDtos.LeaveTypeResponse createLeaveType(
            @Valid @RequestBody LeavePolicyDtos.CreateLeaveTypeRequest request) {
        return leavePolicyService.createLeaveType(request);
    }

    @PutMapping("/api/admin/leave-types/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public LeavePolicyDtos.LeaveTypeResponse updateLeaveType(
            @PathVariable UUID id, @Valid @RequestBody LeavePolicyDtos.UpdateLeaveTypeRequest request) {
        return leavePolicyService.updateLeaveType(id, request);
    }

    @GetMapping("/api/leave-types")
    public List<LeavePolicyDtos.LeaveTypeResponse> listActiveLeaveTypes() {
        return leavePolicyService.listLeaveTypes(true);
    }

    @GetMapping("/api/admin/holidays")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<LeavePolicyDtos.HolidayResponse> adminListHolidays() {
        return leavePolicyService.listHolidays();
    }

    @PostMapping("/api/admin/holidays")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public LeavePolicyDtos.HolidayResponse createHoliday(
            @Valid @RequestBody LeavePolicyDtos.CreateHolidayRequest request) {
        return leavePolicyService.createHoliday(request);
    }

    @GetMapping("/api/holidays")
    public List<LeavePolicyDtos.HolidayResponse> listHolidays() {
        return leavePolicyService.listHolidays();
    }
}
