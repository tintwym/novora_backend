package prod.tint_wym.novora_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.CrmIntegrationDtos;
import prod.tint_wym.novora_backend.service.CrmIntegrationService;

/** HR-admin screens for linking this workspace to Novora CRM. */
@RestController
public class CrmIntegrationAdminController {

    private final CrmIntegrationService crmIntegrationService;

    public CrmIntegrationAdminController(CrmIntegrationService crmIntegrationService) {
        this.crmIntegrationService = crmIntegrationService;
    }

    @GetMapping("/api/admin/integrations/crm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public CrmIntegrationDtos.ConnectionStatus status() {
        return crmIntegrationService.status();
    }

    @PostMapping("/api/admin/integrations/crm/link-code")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CrmIntegrationDtos.LinkCodeResponse createLinkCode(Authentication authentication) {
        return crmIntegrationService.createLinkCode(authentication != null ? authentication.getName() : null);
    }

    @DeleteMapping("/api/admin/integrations/crm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect() {
        crmIntegrationService.disconnect();
    }
}
