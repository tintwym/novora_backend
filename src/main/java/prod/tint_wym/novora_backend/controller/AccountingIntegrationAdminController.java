package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.AccountingIntegrationDtos;
import prod.tint_wym.novora_backend.service.AccountingIntegrationService;

/** HR-admin screens for linking this workspace to Novora Accounting. */
@RestController
public class AccountingIntegrationAdminController {

    private final AccountingIntegrationService accountingIntegrationService;

    public AccountingIntegrationAdminController(AccountingIntegrationService accountingIntegrationService) {
        this.accountingIntegrationService = accountingIntegrationService;
    }

    @GetMapping("/api/admin/integrations/accounting")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public AccountingIntegrationDtos.ConnectionStatus status() {
        return accountingIntegrationService.status();
    }

    @PutMapping("/api/admin/integrations/accounting")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public AccountingIntegrationDtos.ConnectionStatus connect(
            @Valid @RequestBody AccountingIntegrationDtos.ConnectRequest request) {
        return accountingIntegrationService.connect(request);
    }

    @DeleteMapping("/api/admin/integrations/accounting")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect() {
        accountingIntegrationService.disconnect();
    }
}
