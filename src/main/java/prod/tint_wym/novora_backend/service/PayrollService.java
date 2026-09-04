package prod.tint_wym.novora_backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.PayrollDtos;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.Payroll;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.PayrollRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class PayrollService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final BigDecimal DEFAULT_BASIC = new BigDecimal("4500.00");
    private static final BigDecimal DEFAULT_ALLOWANCE = new BigDecimal("300.00");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.08");

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;

    public PayrollService(PayrollRepository payrollRepository, EmployeeRepository employeeRepository) {
        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
    }

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    private Employee employeeForEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.US);
        return employeeRepository.findByAppUser_EmailIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found for user"));
    }

    private static String name(Employee e) {
        return (e.getFirstName() + " " + e.getLastName()).trim();
    }

    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZONE).toInstant();
    }

    private PayrollDtos.PayrollRowResponse toResponse(Payroll p) {
        Employee e = p.getEmployee();
        return new PayrollDtos.PayrollRowResponse(
                p.getId(),
                e.getId(),
                name(e),
                e.getEmployeeCode(),
                p.getPayMonth(),
                p.getPayYear(),
                p.getBasicSalary(),
                nz(p.getAllowances()),
                nz(p.getOvertimePay()),
                nz(p.getBonus()),
                nz(p.getDeductions()),
                nz(p.getTax()),
                p.getNetPay(),
                p.getStatus(),
                toInstant(p.getProcessedAt()),
                toInstant(p.getPaidAt()));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public List<PayrollDtos.PayrollRowResponse> listMonth(int payYear, int payMonth) {
        requireOrganizationId();
        return payrollRepository.findAllByPayYearAndPayMonthOrderByEmployee_FirstNameAsc(payYear, payMonth).stream()
                .map(this::toResponse)
                .toList();
    }

    public PayrollDtos.PayrollRunSummary summarizeMonth(int payYear, int payMonth) {
        List<Payroll> rows =
                payrollRepository.findAllByPayYearAndPayMonthOrderByEmployee_FirstNameAsc(payYear, payMonth);
        BigDecimal totalNet = rows.stream()
                .map(p -> nz(p.getNetPay()))
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        long draft = rows.stream().filter(r -> "draft".equalsIgnoreCase(r.getStatus())).count();
        long processed = rows.stream().filter(r -> "processed".equalsIgnoreCase(r.getStatus())).count();
        long paid = rows.stream().filter(r -> "paid".equalsIgnoreCase(r.getStatus())).count();
        return new PayrollDtos.PayrollRunSummary(
                payMonth, payYear, rows.size(), totalNet, draft, processed, paid);
    }

    public List<PayrollDtos.PayrollRowResponse> myPayslips(String email) {
        Employee e = employeeForEmail(email);
        return payrollRepository.findAllByEmployee_IdOrderByPayYearDescPayMonthDesc(e.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<PayrollDtos.PayrollRowResponse> generateMonth(
            String adminEmail, PayrollDtos.GeneratePayrollRequest request) {
        requireOrganizationId();
        Employee admin = employeeForEmail(adminEmail);
        int month = request.payMonth();
        int year = request.payYear();
        LocalDateTime now = LocalDateTime.now();

        List<Employee> active = employeeRepository.findAllByStatusNotIgnoreCase("terminated");
        for (Employee emp : active) {
            if (!"active".equalsIgnoreCase(emp.getStatus()) && !"on_leave".equalsIgnoreCase(emp.getStatus())) {
                continue;
            }
            if (payrollRepository.findByEmployee_IdAndPayYearAndPayMonth(emp.getId(), year, month).isPresent()) {
                continue;
            }
            BigDecimal basic = DEFAULT_BASIC;
            BigDecimal allowances = DEFAULT_ALLOWANCE;
            BigDecimal overtime = BigDecimal.ZERO;
            BigDecimal bonus = BigDecimal.ZERO;
            BigDecimal deductions = BigDecimal.ZERO;
            BigDecimal gross = basic.add(allowances).add(overtime).add(bonus);
            BigDecimal tax = gross.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(deductions).subtract(tax).setScale(2, RoundingMode.HALF_UP);

            Payroll row = new Payroll();
            row.setEmployee(emp);
            row.setPayMonth(month);
            row.setPayYear(year);
            row.setBasicSalary(basic);
            row.setAllowances(allowances);
            row.setOvertimePay(overtime);
            row.setBonus(bonus);
            row.setDeductions(deductions);
            row.setTax(tax);
            row.setNetPay(net);
            row.setWorkingDays(22);
            row.setPresentDays(22);
            row.setAbsentDays(0);
            row.setStatus("draft");
            row.setProcessedBy(admin);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            payrollRepository.save(row);
        }
        return listMonth(year, month);
    }

    @Transactional
    public PayrollDtos.PayrollRowResponse markProcessed(UUID payrollId) {
        UUID orgId = requireOrganizationId();
        Payroll row = payrollRepository
                .findByIdAndEmployee_OrganizationId(payrollId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll row not found"));
        row.setStatus("processed");
        row.setProcessedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        return toResponse(payrollRepository.save(row));
    }

    @Transactional
    public PayrollDtos.PayrollRunSummary processMonth(int payYear, int payMonth) {
        requireOrganizationId();
        LocalDateTime now = LocalDateTime.now();
        List<Payroll> rows =
                payrollRepository.findAllByPayYearAndPayMonthOrderByEmployee_FirstNameAsc(payYear, payMonth);
        for (Payroll row : rows) {
            if ("draft".equalsIgnoreCase(row.getStatus())) {
                row.setStatus("processed");
                row.setProcessedAt(now);
                row.setUpdatedAt(now);
                payrollRepository.save(row);
            }
        }
        return summarizeMonth(payYear, payMonth);
    }
}
