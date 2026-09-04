package prod.tint_wym.novora_backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.DashboardDtos;
import prod.tint_wym.novora_backend.dto.MyDashboardDtos;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;

@Service
public class MyDashboardService {

    private final EmployeeRepository employeeRepository;
    private final JdbcTemplate jdbc;
    private final DashboardService dashboardService;

    public MyDashboardService(EmployeeRepository employeeRepository, JdbcTemplate jdbc, DashboardService dashboardService) {
        this.employeeRepository = employeeRepository;
        this.jdbc = jdbc;
        this.dashboardService = dashboardService;
    }

    private UUID employeeIdForEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.US);
        return employeeRepository.findByAppUser_EmailIgnoreCase(normalized)
                .map(e -> e.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found for user"));
    }

    public MyDashboardDtos.MyDashboardResponse myDashboard(String email) {
        UUID employeeId = employeeIdForEmail(email);

        // leave_requests.status is stored lowercase ('pending'/'approved'/'rejected') by WorkService;
        // the original 'PENDING' comparison silently produced 0 for every employee.
        long myPendingLeave = jdbc.queryForObject(
                "select count(*) from leave_requests where employee_id = ? and lower(status) = 'pending'",
                Long.class,
                employeeId
        );
        long myLeaveTotal = jdbc.queryForObject(
                "select count(*) from leave_requests where employee_id = ?",
                Long.class,
                employeeId
        );
        // Count remaining (non-completed) onboarding tasks for this employee.
        long myOnboardingRemaining = jdbc.queryForObject(
                """
                select count(*) from onboarding_tasks
                where employee_id = ?
                  and lower(coalesce(status, 'pending')) <> 'completed'
                """,
                Long.class,
                employeeId
        );
        // attendance.status is also stored lowercase ('present'/'late'/'absent'/...); the original
        // 'PRESENT' comparison made every employee's attendance rate read as 0.0%.
        Double myAttendanceRate = jdbc.queryForObject(
                """
                with m as (
                  select date_trunc('month', current_date)::date as start_day,
                         (date_trunc('month', current_date) + interval '1 month' - interval '1 day')::date as end_day
                )
                select
                  case when count(*) = 0 then 0.0
                       else round(100.0 * sum(case when lower(status) = 'present' then 1 else 0 end) / count(*), 1)
                  end as rate
                from attendance al, m
                where al.employee_id = ?
                  and al.work_date between m.start_day and m.end_day
                """,
                Double.class,
                employeeId
        );

        List<DashboardDtos.Kpi> kpis = new ArrayList<>();
        kpis.add(new DashboardDtos.Kpi(
                "Attendance Rate",
                String.format(Locale.US, "%.1f%%", myAttendanceRate == null ? 0.0 : myAttendanceRate),
                "This month",
                "clock",
                "#4f8dff",
                List.of(new DashboardDtos.Point(Instant.now().toString(), myAttendanceRate == null ? 0.0 : myAttendanceRate))
        ));
        kpis.add(new DashboardDtos.Kpi(
                "Leave Requests",
                String.valueOf(myLeaveTotal),
                String.valueOf(myPendingLeave) + " pending",
                "calendar",
                "#a78bfa",
                List.of(new DashboardDtos.Point(Instant.now().toString(), myLeaveTotal))
        ));
        kpis.add(new DashboardDtos.Kpi(
                "Onboarding Tasks",
                String.valueOf(myOnboardingRemaining),
                "remaining",
                "check",
                "#22c55e",
                List.of(new DashboardDtos.Point(Instant.now().toString(), myOnboardingRemaining))
        ));

        // Reuse org-level charts; employees can see aggregate dashboard visuals.
        List<DashboardDtos.GrowthPoint> growth = dashboardService.growth(12);
        List<DashboardDtos.DepartmentSlice> depts = dashboardService.employeesByDepartment();
        DashboardDtos.AttendanceOverview attendance = dashboardService.attendanceOverview();
        List<DashboardDtos.EmployeeRow> recentHires = dashboardService.recentHires(4);
        // leave_requests has leave_type_id (FK to leave_types), not a leave_type column;
        // querying the old column name threw "column does not exist" → 500.
        List<DashboardDtos.LeaveRequestRow> myLeaveRows = jdbc.query(
                """
                select lr.id,
                       e.first_name, e.last_name,
                       lt.name as leave_type,
                       lr.start_date,
                       lr.end_date,
                       lr.status
                from leave_requests lr
                join employees e on e.id = lr.employee_id
                left join leave_types lt on lt.id = lr.leave_type_id
                where lr.employee_id = ?
                order by lr.created_at desc
                limit 5
                """,
                (rs, rowNum) -> new DashboardDtos.LeaveRequestRow(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getString("leave_type"),
                        rs.getDate("start_date").toLocalDate() + " — " + rs.getDate("end_date").toLocalDate(),
                        rs.getString("status")
                ),
                employeeId
        );

        return new MyDashboardDtos.MyDashboardResponse(
                kpis,
                growth,
                depts,
                attendance,
                recentHires,
                myLeaveRows,
                List.of()
        );
    }
}

