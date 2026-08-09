package prod.tint_wym.novora_backend.service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.DashboardDtos;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
public class DashboardService {

    private final JdbcTemplate jdbc;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMM").withZone(ZONE);
    private static final DateTimeFormatter MONTH_DAY_FMT = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZONE);
    // NumberFormat is not thread-safe, so it must not be shared across requests.
    private static String formatInt(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // All employee counts/aggregates below filter out soft-deleted rows (status='terminated').
    // Without this guard, dashboards grow forever every time someone is removed from the HR
    // roster, and the headcount diff that powers "hires this month" can go negative.
    private static final String ACTIVE_EMPLOYEES = "lower(coalesce(status, '')) <> 'terminated'";
    private static final String ACTIVE_IN_ORG = ACTIVE_EMPLOYEES + " and organization_id = ?";
    private static final String ACTIVE_E_IN_ORG =
            "lower(coalesce(e.status, '')) <> 'terminated' and e.organization_id = ?";

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    public DashboardDtos.Summary summary() {
        UUID orgId = requireOrganizationId();
        long totalEmployees = jdbc.queryForObject(
                "select count(*) from employees where " + ACTIVE_IN_ORG, Long.class, orgId);

        long newHires30 = jdbc.queryForObject(
                "select count(*) from employees where " + ACTIVE_IN_ORG
                        + " and created_at >= now() - interval '30 days'",
                Long.class,
                orgId
        );

        long newHiresPrev30 = jdbc.queryForObject(
                "select count(*) from employees where " + ACTIVE_IN_ORG
                        + " and created_at >= now() - interval '60 days'"
                        + " and created_at <  now() - interval '30 days'",
                Long.class,
                orgId
        );

        // Compute hires-this-month directly so a same-month termination can never push this
        // negative (the old subtraction `totalEmployees - headcountStartOfMonth` was monotonic
        // only with hard delete; with soft-delete it can go negative when more employees were
        // terminated this month than hired).
        long hiresThisMonth = jdbc.queryForObject(
                "select count(*) from employees where " + ACTIVE_IN_ORG
                        + " and created_at >= date_trunc('month', current_timestamp)",
                Long.class,
                orgId
        );

        long hiresPrevCalendarMonth = jdbc.queryForObject(
                "select count(*) from employees where " + ACTIVE_IN_ORG
                        + " and created_at >= date_trunc('month', current_timestamp) - interval '1 month'"
                        + " and created_at <  date_trunc('month', current_timestamp)",
                Long.class,
                orgId
        );

        long onLeave = jdbc.queryForObject(
                "select count(*) from employees where status = 'on_leave' and organization_id = ?",
                Long.class,
                orgId
        );

        // Attendance rate this month: rows marked present vs total rows.
        Double attendanceRate = jdbc.queryForObject(
                """
                with m as (
                  select date_trunc('month', current_date)::date as start_day,
                         (date_trunc('month', current_date) + interval '1 month' - interval '1 day')::date as end_day
                )
                select
                  case when count(*) = 0 then 0.0
                       else round(100.0 * sum(case when lower(al.status) = 'present' then 1 else 0 end) / count(*), 1)
                  end as rate
                from attendance al
                join employees e on e.id = al.employee_id, m
                where e.organization_id = ?
                  and al.work_date between m.start_day and m.end_day
                """,
                Double.class,
                orgId
        );

        List<DashboardDtos.Kpi> kpis = new ArrayList<>();
        kpis.add(new DashboardDtos.Kpi(
                "Total Employees",
                formatInt(totalEmployees),
                formatPctChange(hiresPrevCalendarMonth, hiresThisMonth),
                "users",
                "#7c3aed",
                headcountSparkSeries(6)
        ));
        kpis.add(new DashboardDtos.Kpi(
                "New Hires",
                formatInt(newHires30),
                formatPctChange(newHiresPrev30, newHires30),
                "user-plus",
                "#4f8dff",
                monthlyNewHiresSparkSeries(6)
        ));
        kpis.add(new DashboardDtos.Kpi(
                "On Leave",
                formatInt(onLeave),
                "—",
                "calendar",
                "#a78bfa",
                flatSparkSeries(onLeave, 6)
        ));
        kpis.add(new DashboardDtos.Kpi(
                "Attendance Rate",
                String.format(Locale.US, "%.1f%%", attendanceRate == null ? 0.0 : attendanceRate),
                "—",
                "clock",
                "#22c55e",
                flatSparkSeries(Math.round(attendanceRate == null ? 0.0 : attendanceRate), 6)
        ));
        kpis.add(new DashboardDtos.Kpi(
                "Open Positions",
                "0",
                "—",
                "briefcase",
                "#6366f1",
                flatSparkSeries(0, 6)
        ));
        kpis.add(new DashboardDtos.Kpi(
                "Turnover Rate",
                "0.0%",
                "—",
                "turnover",
                "#f97316",
                flatSparkSeries(0, 6)
        ));

        return new DashboardDtos.Summary(kpis);
    }

    public List<DashboardDtos.DepartmentSlice> employeesByDepartment() {
        UUID orgId = requireOrganizationId();
        record Row(String name, long c) {}
        List<Row> rows = jdbc.query(
                "select coalesce(d.name, 'Unassigned') as name, count(*)::bigint as c "
                        + "from employees e "
                        + "left join departments d on d.id = e.department_id "
                        + "where " + ACTIVE_E_IN_ORG + " "
                        + "group by coalesce(d.name, 'Unassigned') "
                        + "order by c desc, name asc",
                (rs, rowNum) -> new Row(rs.getString("name"), rs.getLong("c")),
                orgId
        );
        long total = rows.stream().mapToLong(r -> r.c()).sum();
        if (total <= 0) return List.of();
        return rows.stream()
                .map(r -> new DashboardDtos.DepartmentSlice(
                        r.name(),
                        r.c(),
                        Math.round((1000.0 * r.c() / total)) / 10.0
                ))
                .toList();
    }

    public DashboardDtos.AttendanceOverview attendanceOverview() {
        UUID orgId = requireOrganizationId();
        record C(String status, long c) {}
        List<C> rows = jdbc.query(
                """
                with m as (
                  select date_trunc('month', current_date)::date as start_day,
                         (date_trunc('month', current_date) + interval '1 month' - interval '1 day')::date as end_day
                )
                select upper(al.status) as status, count(*)::bigint as c
                from attendance al
                join employees e on e.id = al.employee_id, m
                where e.organization_id = ?
                  and al.work_date between m.start_day and m.end_day
                group by upper(al.status)
                """,
                (rs, rowNum) -> new C(rs.getString("status"), rs.getLong("c")),
                orgId
        );
        long total = rows.stream().mapToLong(r -> r.c()).sum();
        long present = rows.stream().filter(r -> "PRESENT".equals(r.status())).mapToLong(r -> r.c()).sum();
        double rate = total == 0 ? 0.0 : Math.round((1000.0 * present / total)) / 10.0;
        long absent = rows.stream().filter(r -> "ABSENT".equals(r.status())).mapToLong(r -> r.c()).sum();
        long late = rows.stream().filter(r -> "LATE".equals(r.status())).mapToLong(r -> r.c()).sum();
        long onLeaveAtt = rows.stream()
                .filter(r -> "ON_LEAVE".equals(r.status()) || "HALF_DAY".equals(r.status()))
                .mapToLong(r -> r.c())
                .sum();
        long inBuckets = present + absent + late + onLeaveAtt;
        long other = total - inBuckets;
        java.util.ArrayList<DashboardDtos.AttendanceBucket> buckets = new java.util.ArrayList<>();
        buckets.add(new DashboardDtos.AttendanceBucket("Present", present));
        buckets.add(new DashboardDtos.AttendanceBucket("Absent", absent));
        buckets.add(new DashboardDtos.AttendanceBucket("Late", late));
        buckets.add(new DashboardDtos.AttendanceBucket("On Leave", onLeaveAtt));
        if (other > 0) {
            buckets.add(new DashboardDtos.AttendanceBucket("Other", other));
        }
        return new DashboardDtos.AttendanceOverview(rate, List.copyOf(buckets));
    }

    public List<DashboardDtos.LeaveRequestRow> leaveRequests(int limit) {
        UUID orgId = requireOrganizationId();
        int lim = Math.max(1, Math.min(limit, 20));
        return jdbc.query(
                """
                select lr.id,
                       e.first_name, e.last_name,
                       lt.name as leave_type,
                       lr.start_date,
                       lr.end_date,
                       lr.status
                from leave_requests lr
                join employees e on e.id = lr.employee_id
                join leave_types lt on lt.id = lr.leave_type_id
                where e.organization_id = ?
                order by lr.created_at desc
                limit ?
                """,
                (rs, rowNum) -> {
                    LocalDate s = rs.getDate("start_date").toLocalDate();
                    LocalDate ed = rs.getDate("end_date").toLocalDate();
                    String range = MONTH_DAY_FMT.format(s) + " — " + MONTH_DAY_FMT.format(ed);
                    return new DashboardDtos.LeaveRequestRow(
                            rs.getObject("id", UUID.class),
                            formatPersonDisplayName(rs.getString("first_name"), rs.getString("last_name")),
                            rs.getString("leave_type"),
                            range,
                            rs.getString("status")
                    );
                },
                orgId,
                lim
        );
    }

    public List<DashboardDtos.GrowthPoint> growth(int months) {
        UUID orgId = requireOrganizationId();
        int m = Math.max(1, Math.min(months, 24));
        return jdbc.query(
                "select gs as bucket_month, "
                        + "       coalesce(( "
                        + "           select count(*) from employees e "
                        + "           where date_trunc('month', e.created_at) = gs "
                        + "             and " + ACTIVE_E_IN_ORG + " "
                        + "       ), 0) as c "
                        + "from generate_series( "
                        + "  date_trunc('month', current_timestamp) - (?::int - 1) * interval '1 month', "
                        + "  date_trunc('month', current_timestamp), "
                        + "  interval '1 month' "
                        + ") as gs "
                        + "order by gs",
                (rs, rowNum) -> new DashboardDtos.GrowthPoint(
                        MONTH_FMT.format(rs.getTimestamp("bucket_month").toInstant()),
                        rs.getLong("c")),
                orgId,
                m);
    }

    public List<DashboardDtos.EmployeeRow> recentHires(int limit) {
        UUID orgId = requireOrganizationId();
        int lim = Math.max(1, Math.min(limit, 20));
        return jdbc.query(
                "select e.id, e.first_name, e.last_name, e.created_at, d.name as dept_name, "
                        + "       coalesce(nullif(trim(p.title), ''), '') as job_title "
                        + "from employees e "
                        + "left join departments d on d.id = e.department_id "
                        + "left join positions p on p.id = e.position_id "
                        + "where " + ACTIVE_E_IN_ORG + " "
                        + "order by e.hire_date desc nulls last, e.created_at desc "
                        + "limit ?",
                (rs, rowNum) -> {
                    String dept = rs.getString("dept_name");
                    String tag = dept != null && !dept.isBlank() ? dept : "Employee";
                    String title = rs.getString("job_title");
                    String role = title != null && !title.isBlank() ? title : "Employee";
                    // created_at is nullable on legacy/backfilled rows; don't NPE the whole endpoint.
                    java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                    String joined = createdAt != null ? DATE_FMT.format(createdAt.toInstant()) : "—";
                    return new DashboardDtos.EmployeeRow(
                            rs.getObject("id", UUID.class),
                            formatPersonDisplayName(rs.getString("first_name"), rs.getString("last_name")),
                            role,
                            joined,
                            tag
                    );
                },
                orgId,
                lim
        );
    }

    public List<DashboardDtos.LeaveRow> leaveOverview() {
        UUID orgId = requireOrganizationId();
        return jdbc.query(
                """
                select lt.name as label,
                       coalesce(sum(lb.used_days), 0)::int as used,
                       greatest(lt.days_allowed, 1) as total,
                       '#6366f1' as color
                from leave_types lt
                left join leave_balances lb
                  on lb.leave_type_id = lt.id
                 and lb.balance_year = extract(year from current_date)::int
                where lt.organization_id = ?
                group by lt.id, lt.name, lt.days_allowed
                order by lt.name
                """,
                (rs, rowNum) -> new DashboardDtos.LeaveRow(
                        rs.getString("label"),
                        rs.getInt("used"),
                        rs.getInt("total"),
                        Objects.requireNonNullElse(rs.getString("color"), "#6366f1")
                ),
                orgId
        );
    }

    public List<DashboardDtos.PayrollSlice> payrollSummary() {
        UUID orgId = requireOrganizationId();
        YearMonth ym = YearMonth.now(ZONE);
        Optional<long[]> row = fetchPayrollRow(ym, orgId);
        if (row.isEmpty()) {
            return List.of();
        }
        long[] v = row.get();
        List<DashboardDtos.PayrollSlice> slices = new ArrayList<>();
        if (v[0] > 0) slices.add(new DashboardDtos.PayrollSlice("Basic Salary", v[0], "#4f8dff"));
        if (v[1] > 0) slices.add(new DashboardDtos.PayrollSlice("Allowances", v[1], "#7c3aed"));
        if (v[2] > 0) slices.add(new DashboardDtos.PayrollSlice("Deductions", v[2], "#a78bfa"));
        if (v[3] > 0) slices.add(new DashboardDtos.PayrollSlice("Overtime", v[3], "#c4b5fd"));
        return slices;
    }

    public List<DashboardDtos.BirthdayRow> birthdays(int limit) {
        UUID orgId = requireOrganizationId();
        int lim = Math.max(1, Math.min(limit, 30));
        LocalDate today = LocalDate.now(ZONE);
        record DobRow(String firstName, String lastName, String jobTitle, LocalDate dob) {}

        List<DobRow> rows = jdbc.query(
                "select first_name, last_name, "
                        + "       coalesce(nullif(trim(p.title), ''), 'Employee') as job_title, "
                        + "       date_of_birth "
                        + "from employees e "
                        + "left join positions p on p.id = e.position_id "
                        + "where date_of_birth is not null "
                        + "  and " + ACTIVE_E_IN_ORG,
                (rs, rowNum) -> new DobRow(
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("job_title"),
                        rs.getDate("date_of_birth").toLocalDate()),
                orgId);

        return rows.stream()
                .sorted(Comparator.comparing(r -> nextBirthdayDate(today, r.dob())))
                .limit(lim)
                .map(r -> new DashboardDtos.BirthdayRow(
                        formatPersonDisplayName(r.firstName(), r.lastName()),
                        r.jobTitle(),
                        MonthDay.from(r.dob()).format(MONTH_DAY_FMT)))
                .toList();
    }

    public List<DashboardDtos.TaskRow> tasks(int limit) {
        return List.of();
    }

    private Optional<long[]> fetchPayrollRow(YearMonth ym, UUID orgId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    select (coalesce(sum(hp.basic_salary), 0) * 100)::bigint as basic_salary,
                           (coalesce(sum(hp.allowances), 0) * 100)::bigint as allowances,
                           (coalesce(sum(hp.deductions), 0) * 100)::bigint as deductions,
                           (coalesce(sum(hp.overtime_pay), 0) * 100)::bigint as overtime
                    from hr_payroll hp
                    join employees e on e.id = hp.employee_id
                    where e.organization_id = ?
                      and hp.pay_year = ? and hp.pay_month = ?
                    """,
                    (rs, rowNum) -> new long[] {
                        rs.getLong("basic_salary"),
                        rs.getLong("allowances"),
                        rs.getLong("deductions"),
                        rs.getLong("overtime")
                    },
                    orgId,
                    ym.getYear(),
                    ym.getMonthValue()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private List<DashboardDtos.Point> headcountSparkSeries(int months) {
        UUID orgId = requireOrganizationId();
        int m = Math.max(1, Math.min(months, 24));
        return jdbc.query(
                "select gs as bucket_month, "
                        + "       (select count(*) from employees e "
                        + "        where e.created_at < gs + interval '1 month' "
                        + "          and " + ACTIVE_E_IN_ORG + ") as cnt "
                        + "from generate_series( "
                        + "  date_trunc('month', current_timestamp) - (?::int - 1) * interval '1 month', "
                        + "  date_trunc('month', current_timestamp), "
                        + "  interval '1 month' "
                        + ") as gs "
                        + "order by gs",
                (rs, rowNum) -> new DashboardDtos.Point(
                        MONTH_FMT.format(rs.getTimestamp("bucket_month").toInstant()),
                        rs.getDouble("cnt")),
                orgId,
                m);
    }

    private List<DashboardDtos.Point> monthlyNewHiresSparkSeries(int months) {
        UUID orgId = requireOrganizationId();
        int m = Math.max(1, Math.min(months, 24));
        return jdbc.query(
                "select gs as bucket_month, "
                        + "       coalesce(( "
                        + "           select count(*)::double precision from employees e "
                        + "           where date_trunc('month', e.created_at) = gs "
                        + "             and " + ACTIVE_E_IN_ORG + " "
                        + "       ), 0) as c "
                        + "from generate_series( "
                        + "  date_trunc('month', current_timestamp) - (?::int - 1) * interval '1 month', "
                        + "  date_trunc('month', current_timestamp), "
                        + "  interval '1 month' "
                        + ") as gs "
                        + "order by gs",
                (rs, rowNum) -> new DashboardDtos.Point(
                        MONTH_FMT.format(rs.getTimestamp("bucket_month").toInstant()),
                        rs.getDouble("c")),
                orgId,
                m);
    }

    private List<DashboardDtos.Point> flatSparkSeries(long value, int months) {
        int m = Math.max(1, Math.min(months, 24));
        return jdbc.query(
                """
                select gs as bucket_month from generate_series(
                  date_trunc('month', current_timestamp) - (?::int - 1) * interval '1 month',
                  date_trunc('month', current_timestamp),
                  interval '1 month'
                ) as gs
                order by gs
                """,
                (rs, rowNum) -> new DashboardDtos.Point(
                        MONTH_FMT.format(rs.getTimestamp("bucket_month").toInstant()),
                        (double) value),
                m);
    }

    private static LocalDate nextBirthdayDate(LocalDate today, LocalDate dob) {
        MonthDay md = MonthDay.from(dob);
        LocalDate next = birthdayInYear(md, today.getYear());
        if (next.isBefore(today)) {
            next = birthdayInYear(md, today.getYear() + 1);
        }
        return next;
    }

    /** Feb 29 DOBs fall on Mar 1 in non-leap years (MonthDay.atYear would throw). */
    private static LocalDate birthdayInYear(MonthDay md, int year) {
        try {
            return md.atYear(year);
        } catch (java.time.DateTimeException ex) {
            return LocalDate.of(year, 3, 1);
        }
    }

    private static String formatPctChange(long baseline, long current) {
        if (baseline <= 0 && current <= 0) {
            return "—";
        }
        if (baseline <= 0) {
            return "+100%";
        }
        double pct = (current - baseline) * 100.0 / baseline;
        return String.format(Locale.US, "%+.1f%%", pct);
    }

    /**
     * Human-readable name for dashboard rows. Registration bootstrapping sets last name to "Employee"
     * when the email local-part has only one segment; omit that placeholder so the UI shows a given name only.
     */
    private static String formatPersonDisplayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        if (first.isEmpty() && last.isEmpty()) {
            return "—";
        }
        if (last.isEmpty()) {
            return first.isEmpty() ? "—" : first;
        }
        if ("Employee".equalsIgnoreCase(last)) {
            return first.isEmpty() ? last : first;
        }
        if (first.isEmpty()) {
            return last;
        }
        return first + " " + last;
    }
}

