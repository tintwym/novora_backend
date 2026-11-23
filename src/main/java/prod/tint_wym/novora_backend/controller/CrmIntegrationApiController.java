package prod.tint_wym.novora_backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.CrmIntegrationDtos;
import prod.tint_wym.novora_backend.integration.CrmIntegrationSettings;
import prod.tint_wym.novora_backend.integration.RequestSigner;
import prod.tint_wym.novora_backend.service.CrmIntegrationService;
import prod.tint_wym.novora_backend.tenancy.TenantContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Server-to-server endpoints called by Novora CRM. There is no signed-in user here: every request
 * must carry a valid {@link RequestSigner} signature, and data access is limited to a workspace
 * that an HR admin explicitly linked with a one-time code.
 */
@RestController
@RequestMapping(CrmIntegrationApiController.BASE_PATH)
public class CrmIntegrationApiController {

    public static final String BASE_PATH = "/api/integrations/crm/v1";
    public static final String CRM_COMPANY_HEADER = "X-Novora-Crm-Company";

    private final CrmIntegrationService crmIntegrationService;
    private final CrmIntegrationSettings settings;
    private final JsonMapper jsonMapper;

    public CrmIntegrationApiController(
            CrmIntegrationService crmIntegrationService,
            CrmIntegrationSettings settings,
            JsonMapper jsonMapper) {
        this.crmIntegrationService = crmIntegrationService;
        this.settings = settings;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/link/redeem")
    public CrmIntegrationDtos.RedeemResponse redeem(
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        verifySignature(request, body);
        CrmIntegrationDtos.RedeemRequest parsed;
        try {
            parsed = jsonMapper.readValue(body, CrmIntegrationDtos.RedeemRequest.class);
        } catch (JacksonException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request body");
        }
        return crmIntegrationService.redeem(parsed.code(), parsed.crmCompanyId());
    }

    @GetMapping("/organizations/{organizationId}/employees")
    public CrmIntegrationDtos.EmployeeListResponse employees(
            @PathVariable UUID organizationId,
            @RequestHeader(value = CRM_COMPANY_HEADER, required = false) String crmCompanyId,
            HttpServletRequest request) {
        verifySignature(request, "");
        // Set the tenant before the service opens its transaction: Hibernate snapshots it then.
        TenantContext.set(organizationId);
        try {
            return crmIntegrationService.listEmployees(organizationId, crmCompanyId);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Answer errors directly. Letting them fall through to the servlet {@code /error} page would
     * re-run authorization for this anonymous caller and turn every error into a bare 401.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }

    private void verifySignature(HttpServletRequest request, String body) {
        if (!settings.isReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Novora CRM integration is not configured on this server");
        }
        String query = request.getQueryString();
        String pathAndQuery = request.getRequestURI() + (query == null || query.isEmpty() ? "" : "?" + query);
        boolean valid = RequestSigner.verify(
                settings.secret(),
                request.getHeader(RequestSigner.TIMESTAMP_HEADER),
                request.getHeader(RequestSigner.SIGNATURE_HEADER),
                request.getMethod(),
                pathAndQuery,
                body == null ? "" : body,
                Instant.now());
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired request signature");
        }
    }
}
