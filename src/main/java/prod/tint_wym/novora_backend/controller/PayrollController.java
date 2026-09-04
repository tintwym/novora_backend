package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.PayrollDtos;
import prod.tint_wym.novora_backend.service.PayrollService;

@RestController
@PreAuthorize("isAuthenticated()")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @GetMapping("/api/my/payslips")
    public List<PayrollDtos.PayrollRowResponse> myPayslips(Authentication auth) {
        return payrollService.myPayslips(auth.getName());
    }

    @GetMapping("/api/admin/payroll")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<PayrollDtos.PayrollRowResponse> listMonth(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        return payrollService.listMonth(y, m);
    }

    @GetMapping("/api/admin/payroll/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public PayrollDtos.PayrollRunSummary summary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        return payrollService.summarizeMonth(y, m);
    }

    @PostMapping("/api/admin/payroll/generate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PayrollDtos.PayrollRowResponse> generate(
            Authentication auth, @Valid @RequestBody PayrollDtos.GeneratePayrollRequest request) {
        return payrollService.generateMonth(auth.getName(), request);
    }

    @PostMapping("/api/admin/payroll/process")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public PayrollDtos.PayrollRunSummary processMonth(
            @RequestParam Integer year, @RequestParam Integer month) {
        return payrollService.processMonth(year, month);
    }

    @PostMapping("/api/admin/payroll/{payrollId}/process")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public PayrollDtos.PayrollRowResponse processOne(@PathVariable UUID payrollId) {
        return payrollService.markProcessed(payrollId);
    }
}
