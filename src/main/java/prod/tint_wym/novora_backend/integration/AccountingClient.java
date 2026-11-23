package prod.tint_wym.novora_backend.integration;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client for Novora Accounting inbound ERP endpoints.
 */
@Component
public class AccountingClient {

    static final String PAYROLL_PATH = "/api/v1/integrations/hrms/payroll-approved";

    private final AccountingIntegrationSettings settings;
    private final RestClient restClient;

    public AccountingClient(AccountingIntegrationSettings settings) {
        this.settings = settings;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public void postPayrollApproved(
            UUID accountingCompanyId,
            String payrollRunId,
            LocalDate payrollDate,
            BigDecimal grossSalary,
            BigDecimal taxWithheld,
            BigDecimal netPayable,
            BigDecimal otherDeductions,
            String description) {
        if (!settings.isReady()) {
            throw new IllegalStateException("Accounting integration is not configured");
        }
        Map<String, Object> body = Map.of(
                "payrollRunId", payrollRunId,
                "payrollDate", payrollDate.toString(),
                "grossSalary", grossSalary,
                "taxWithheld", taxWithheld,
                "netPayable", netPayable,
                "description", description == null ? "" : description,
                "otherDeductions", otherDeductions == null ? BigDecimal.ZERO : otherDeductions);
        try {
            restClient
                    .post()
                    .uri(settings.baseUrl() + PAYROLL_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Company-Id", accountingCompanyId.toString())
                    .header("X-Actor", "hrms")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            String detail = ex.getResponseBodyAsString();
            if (detail == null || detail.isBlank()) {
                detail = ex.getStatusText();
            }
            throw new IllegalStateException(
                    "Accounting rejected payroll push (" + ex.getStatusCode().value() + "): " + trim(detail),
                    ex);
        }
    }

    private static String trim(String value) {
        if (value == null) return "";
        String t = value.trim();
        return t.length() > 400 ? t.substring(0, 400) + "…" : t;
    }
}
