package prod.tint_wym.novora_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.LeaveType;

class LeaveAccrualTest {

    @Test
    void annualEntitlementIsFullAllowance() {
        Employee emp = new Employee();
        emp.setHireDate(LocalDate.of(2020, 1, 15));
        LeaveType type = new LeaveType();
        type.setDaysAllowed(18);
        type.setAccrualMethod("ANNUAL");
        assertEquals(0, WorkService.entitledDays(emp, type, LocalDate.now().getYear())
                .compareTo(BigDecimal.valueOf(18)));
    }

    @Test
    void monthlyProrateUsesEligibleMonths() {
        Employee emp = new Employee();
        emp.setHireDate(LocalDate.of(LocalDate.now().getYear(), 1, 1));
        LeaveType type = new LeaveType();
        type.setDaysAllowed(12);
        type.setAccrualMethod("MONTHLY_PRORATE");
        int month = LocalDate.now().getMonthValue();
        BigDecimal expected = BigDecimal.valueOf(12)
                .multiply(BigDecimal.valueOf(month))
                .divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
        assertEquals(0, WorkService.entitledDays(emp, type, LocalDate.now().getYear()).compareTo(expected));
    }
}
