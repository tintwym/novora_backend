package prod.tint_wym.novora_backend.controller;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.DashboardDtos;
import prod.tint_wym.novora_backend.service.DashboardService;

@RestController
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/admin/dashboard/summary")
    public DashboardDtos.Summary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/api/admin/dashboard/growth")
    public List<DashboardDtos.GrowthPoint> growth(@RequestParam(defaultValue = "6") int months) {
        return dashboardService.growth(months);
    }

    @GetMapping("/api/admin/dashboard/recent-hires")
    public List<DashboardDtos.EmployeeRow> recentHires(@RequestParam(defaultValue = "4") int limit) {
        return dashboardService.recentHires(limit);
    }

    @GetMapping("/api/admin/dashboard/leave-overview")
    public List<DashboardDtos.LeaveRow> leaveOverview() {
        return dashboardService.leaveOverview();
    }

    @GetMapping("/api/admin/dashboard/payroll-summary")
    public List<DashboardDtos.PayrollSlice> payrollSummary() {
        return dashboardService.payrollSummary();
    }

    @GetMapping("/api/admin/dashboard/birthdays")
    public List<DashboardDtos.BirthdayRow> birthdays(@RequestParam(defaultValue = "8") int limit) {
        return dashboardService.birthdays(limit);
    }

    @GetMapping("/api/admin/dashboard/tasks")
    public List<DashboardDtos.TaskRow> tasks(@RequestParam(defaultValue = "12") int limit) {
        return dashboardService.tasks(limit);
    }

    @GetMapping("/api/admin/dashboard/departments")
    public List<DashboardDtos.DepartmentSlice> departments() {
        return dashboardService.employeesByDepartment();
    }

    @GetMapping("/api/admin/dashboard/attendance-overview")
    public DashboardDtos.AttendanceOverview attendanceOverview() {
        return dashboardService.attendanceOverview();
    }

    @GetMapping("/api/admin/dashboard/leave-requests")
    public List<DashboardDtos.LeaveRequestRow> leaveRequests(@RequestParam(defaultValue = "5") int limit) {
        return dashboardService.leaveRequests(limit);
    }
}
