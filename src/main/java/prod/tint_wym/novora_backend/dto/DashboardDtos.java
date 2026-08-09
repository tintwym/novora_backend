package prod.tint_wym.novora_backend.dto;

import java.util.List;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record Point(String x, double y) {}

    public record Kpi(
            String label,
            String value,
            String delta,
            String icon,
            String accent,
            List<Point> series
    ) {}

    public record Summary(
            List<Kpi> kpis
    ) {}

    public record GrowthPoint(String month, long employees) {}

    public record EmployeeRow(
            UUID id,
            String name,
            String role,
            String date,
            String tag
    ) {}

    public record LeaveRow(
            String label,
            int used,
            int total,
            String color
    ) {}

    public record PayrollSlice(
            String name,
            long value,
            String fill
    ) {}

    public record BirthdayRow(
            String name,
            String role,
            String date
    ) {}

    public record TaskRow(
            String text,
            String status
    ) {}

    public record DepartmentSlice(
            String name,
            long count,
            double percent
    ) {}

    public record AttendanceBucket(
            String label,
            long count
    ) {}

    public record AttendanceOverview(
            double attendanceRate,
            List<AttendanceBucket> buckets
    ) {}

    public record LeaveRequestRow(
            UUID id,
            String name,
            String leaveType,
            String dateRange,
            String status
    ) {}
}

