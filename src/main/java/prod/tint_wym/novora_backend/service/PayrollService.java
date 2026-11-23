package prod.tint_wym.novora_backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.PayrollDtos;
import prod.tint_wym.novora_backend.entity.AllowanceType;
import prod.tint_wym.novora_backend.entity.DeductionType;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.OtPolicy;
import prod.tint_wym.novora_backend.entity.OvertimeRecord;
import prod.tint_wym.novora_backend.entity.Payroll;
import prod.tint_wym.novora_backend.entity.TaxCategory;
import prod.tint_wym.novora_backend.repository.AllowanceTypeRepository;
import prod.tint_wym.novora_backend.repository.DeductionTypeRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.OtPolicyRepository;
import prod.tint_wym.novora_backend.repository.OvertimeRecordRepository;
import prod.tint_wym.novora_backend.repository.PayrollRepository;
import prod.tint_wym.novora_backend.repository.TaxCategoryRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class PayrollService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    /** Fallback only when the employee has no basic salary set. */
    private static final BigDecimal FALLBACK_BASIC = new BigDecimal("4500.00");
    private static final BigDecimal FALLBACK_TAX_RATE = new BigDecimal("0.08");
    private static final BigDecimal STANDARD_MONTH_HOURS = new BigDecimal("176"); // 22 days × 8h

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final AllowanceTypeRepository allowanceTypeRepository;
    private final DeductionTypeRepository deductionTypeRepository;
    private final TaxCategoryRepository taxCategoryRepository;
    private final OtPolicyRepository otPolicyRepository;
    private final OvertimeRecordRepository overtimeRecordRepository;
    private final AccountingIntegrationService accountingIntegrationService;

    public PayrollService(
            PayrollRepository payrollRepository,
            EmployeeRepository employeeRepository,
            AllowanceTypeRepository allowanceTypeRepository,
            DeductionTypeRepository deductionTypeRepository,
            TaxCategoryRepository taxCategoryRepository,
            OtPolicyRepository otPolicyRepository,
            OvertimeRecordRepository overtimeRecordRepository,
            AccountingIntegrationService accountingIntegrationService) {
        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
        this.allowanceTypeRepository = allowanceTypeRepository;
        this.deductionTypeRepository = deductionTypeRepository;
        this.taxCategoryRepository = taxCategoryRepository;
        this.otPolicyRepository = otPolicyRepository;
        this.overtimeRecordRepository = overtimeRecordRepository;
        this.accountingIntegrationService = accountingIntegrationService;
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

    private static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
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
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        BigDecimal catalogAllowances = sumMonthlyAllowances();
        BigDecimal catalogDeductions = sumMonthlyDeductions();
        BigDecimal taxRate = resolveTaxRate(null);
        BigDecimal otMultiplier = resolveWeekdayOtMultiplier();

        List<Employee> active = employeeRepository.findAllByStatusNotIgnoreCase("terminated");
        for (Employee emp : active) {
            if (!"active".equalsIgnoreCase(emp.getStatus()) && !"on_leave".equalsIgnoreCase(emp.getStatus())) {
                continue;
            }
            if (payrollRepository.findByEmployee_IdAndPayYearAndPayMonth(emp.getId(), year, month).isPresent()) {
                continue;
            }

            BigDecimal basic = resolveBasic(emp);
            BigDecimal allowances = emp.getMonthlyAllowance() != null
                    ? money(emp.getMonthlyAllowance())
                    : catalogAllowances;
            BigDecimal deductions = emp.getMonthlyDeduction() != null
                    ? money(emp.getMonthlyDeduction())
                    : catalogDeductions;
            BigDecimal overtime = money(calculateOvertimePay(emp, basic, from, to, otMultiplier));
            BigDecimal bonus = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal employeeTaxRate = resolveTaxRate(emp);
            BigDecimal gross = money(basic.add(allowances).add(overtime).add(bonus));
            BigDecimal tax = money(gross.multiply(employeeTaxRate != null ? employeeTaxRate : taxRate));
            BigDecimal net = money(gross.subtract(deductions).subtract(tax));

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

    private BigDecimal resolveBasic(Employee emp) {
        if (emp.getBasicSalary() != null && emp.getBasicSalary().compareTo(BigDecimal.ZERO) > 0) {
            return money(emp.getBasicSalary());
        }
        return money(FALLBACK_BASIC);
    }

    private BigDecimal sumMonthlyAllowances() {
        return allowanceTypeRepository.findAllByOrderByNameAsc().stream()
                .filter(AllowanceType::isActive)
                .filter(a -> "monthly".equalsIgnoreCase(a.getFrequency()))
                .map(a -> nz(a.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumMonthlyDeductions() {
        return deductionTypeRepository.findAllByOrderByNameAsc().stream()
                .filter(DeductionType::isActive)
                .filter(d -> "monthly".equalsIgnoreCase(d.getFrequency()))
                .map(d -> nz(d.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * @param emp when non-null, prefers employee tax_rate_percent; otherwise org catalog / fallback.
     * @return fraction (0.08 for 8%), never null
     */
    private BigDecimal resolveTaxRate(Employee emp) {
        if (emp != null && emp.getTaxRatePercent() != null && emp.getTaxRatePercent().compareTo(BigDecimal.ZERO) >= 0) {
            return emp.getTaxRatePercent().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        }
        return taxCategoryRepository.findAllByOrderByNameAsc().stream()
                .filter(TaxCategory::isActive)
                .map(TaxCategory::getRate)
                .filter(r -> r != null && r.compareTo(BigDecimal.ZERO) >= 0)
                .findFirst()
                .map(r -> {
                    // Catalog stores either fraction (0.08) or percent (8). Treat values > 1 as percent.
                    if (r.compareTo(BigDecimal.ONE) > 0) {
                        return r.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
                    }
                    return r;
                })
                .orElse(FALLBACK_TAX_RATE);
    }

    private BigDecimal resolveWeekdayOtMultiplier() {
        return otPolicyRepository.findAllByOrderByNameAsc().stream()
                .filter(OtPolicy::isActive)
                .map(OtPolicy::getWeekdayMultiplier)
                .filter(m -> m != null && m.compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElse(new BigDecimal("1.50"));
    }

    private BigDecimal calculateOvertimePay(
            Employee emp,
            BigDecimal basic,
            LocalDate from,
            LocalDate to,
            BigDecimal weekdayMultiplier) {
        List<OvertimeRecord> rows =
                overtimeRecordRepository.findApprovedForEmployeeInRange(emp.getId(), from, to);
        if (rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal hourly = basic.divide(STANDARD_MONTH_HOURS, 6, RoundingMode.HALF_UP);
        BigDecimal totalHours = rows.stream()
                .map(o -> nz(o.getHours()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return hourly.multiply(totalHours).multiply(weekdayMultiplier);
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
        Payroll saved = payrollRepository.save(row);
        accountingIntegrationService.pushProcessedPayroll(
                orgId,
                saved.getPayYear(),
                saved.getPayMonth(),
                payrollRepository.findAllByPayYearAndPayMonthOrderByEmployee_FirstNameAsc(
                        saved.getPayYear(), saved.getPayMonth()));
        return toResponse(saved);
    }

    @Transactional
    public PayrollDtos.PayrollRunSummary processMonth(int payYear, int payMonth) {
        UUID orgId = requireOrganizationId();
        LocalDateTime now = LocalDateTime.now();
        List<Payroll> rows =
                payrollRepository.findAllByPayYearAndPayMonthOrderByEmployee_FirstNameAsc(payYear, payMonth);
        boolean changed = false;
        for (Payroll row : rows) {
            if ("draft".equalsIgnoreCase(row.getStatus())) {
                row.setStatus("processed");
                row.setProcessedAt(now);
                row.setUpdatedAt(now);
                payrollRepository.save(row);
                changed = true;
            }
        }
        if (changed) {
            accountingIntegrationService.pushProcessedPayroll(
                    orgId,
                    payYear,
                    payMonth,
                    payrollRepository.findAllByPayYearAndPayMonthOrderByEmployee_FirstNameAsc(payYear, payMonth));
        }
        return summarizeMonth(payYear, payMonth);
    }
}
