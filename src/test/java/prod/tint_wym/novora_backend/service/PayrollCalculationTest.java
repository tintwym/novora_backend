package prod.tint_wym.novora_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prod.tint_wym.novora_backend.dto.PayrollDtos;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.OvertimeRecord;
import prod.tint_wym.novora_backend.entity.Payroll;
import prod.tint_wym.novora_backend.repository.AllowanceTypeRepository;
import prod.tint_wym.novora_backend.repository.DeductionTypeRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.OtPolicyRepository;
import prod.tint_wym.novora_backend.repository.OvertimeRecordRepository;
import prod.tint_wym.novora_backend.repository.PayrollRepository;
import prod.tint_wym.novora_backend.repository.TaxCategoryRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@ExtendWith(MockitoExtension.class)
class PayrollCalculationTest {

    @Mock private PayrollRepository payrollRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AllowanceTypeRepository allowanceTypeRepository;
    @Mock private DeductionTypeRepository deductionTypeRepository;
    @Mock private TaxCategoryRepository taxCategoryRepository;
    @Mock private OtPolicyRepository otPolicyRepository;
    @Mock private OvertimeRecordRepository overtimeRecordRepository;

    private PayrollService payrollService;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        TenantContext.set(orgId);
        payrollService = new PayrollService(
                payrollRepository,
                employeeRepository,
                allowanceTypeRepository,
                deductionTypeRepository,
                taxCategoryRepository,
                otPolicyRepository,
                overtimeRecordRepository,
                org.mockito.Mockito.mock(AccountingIntegrationService.class));
        when(allowanceTypeRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(deductionTypeRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(taxCategoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(otPolicyRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
    }

    @Test
    void generateMonthUsesEmployeeCompensationAndApprovedOt() {
        AppUser adminUser = new AppUser();
        adminUser.setEmail("admin@novora.test");
        Employee admin = new Employee();
        admin.setId(UUID.randomUUID());
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setEmployeeCode("E-ADMIN");
        admin.setAppUser(adminUser);
        admin.setStatus("active");

        Employee worker = new Employee();
        worker.setId(UUID.randomUUID());
        worker.setFirstName("Alex");
        worker.setLastName("Rivera");
        worker.setEmployeeCode("E-ALEX");
        worker.setStatus("active");
        worker.setBasicSalary(new BigDecimal("5000.00"));
        worker.setMonthlyAllowance(new BigDecimal("200.00"));
        worker.setMonthlyDeduction(new BigDecimal("100.00"));
        worker.setTaxRatePercent(new BigDecimal("10.00")); // 10%

        OvertimeRecord ot = new OvertimeRecord();
        ot.setEmployee(worker);
        ot.setHours(new BigDecimal("8.00"));
        ot.setStatus("approved");
        ot.setWorkDate(LocalDate.of(2026, 3, 10));

        when(employeeRepository.findByAppUser_EmailIgnoreCase("admin@novora.test"))
                .thenReturn(Optional.of(admin));
        when(employeeRepository.findAllByStatusNotIgnoreCase("terminated"))
                .thenReturn(List.of(worker));
        when(payrollRepository.findByEmployee_IdAndPayYearAndPayMonth(worker.getId(), 2026, 3))
                .thenReturn(Optional.empty());
        when(overtimeRecordRepository.findApprovedForEmployeeInRange(
                        eq(worker.getId()), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(ot));
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payrollRepository.findAllByPayYearAndPayMonthOrderByEmployee_FirstNameAsc(2026, 3))
                .thenReturn(List.of());

        payrollService.generateMonth(
                "admin@novora.test", new PayrollDtos.GeneratePayrollRequest(3, 2026));

        ArgumentCaptor<Payroll> captor = ArgumentCaptor.forClass(Payroll.class);
        org.mockito.Mockito.verify(payrollRepository).save(captor.capture());
        Payroll saved = captor.getValue();

        assertEquals(new BigDecimal("5000.00"), saved.getBasicSalary());
        assertEquals(new BigDecimal("200.00"), saved.getAllowances());
        assertEquals(new BigDecimal("100.00"), saved.getDeductions());
        // hourly = 5000/176 ≈ 28.409091; OT = 28.409091 * 8 * 1.5 ≈ 340.91
        assertEquals(new BigDecimal("340.91"), saved.getOvertimePay());
        // gross = 5000 + 200 + 340.91 = 5540.91; tax 10% = 554.09; net = 5540.91 - 100 - 554.09 = 4886.82
        assertEquals(new BigDecimal("554.09"), saved.getTax());
        assertEquals(new BigDecimal("4886.82"), saved.getNetPay());
        assertEquals("draft", saved.getStatus());
    }
}
