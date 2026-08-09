package prod.tint_wym.novora_backend.dto;

import java.util.List;

public final class MyDashboardDtos {
    private MyDashboardDtos() {}

    public record MyDashboardResponse(
            List<DashboardDtos.Kpi> kpis,
            List<DashboardDtos.GrowthPoint> growth,
            List<DashboardDtos.DepartmentSlice> departments,
            DashboardDtos.AttendanceOverview attendanceOverview,
            List<DashboardDtos.EmployeeRow> recentHires,
            List<DashboardDtos.LeaveRequestRow> leaveRequests,
            List<DashboardDtos.PayrollSlice> payrollSummary
    ) {}
}

