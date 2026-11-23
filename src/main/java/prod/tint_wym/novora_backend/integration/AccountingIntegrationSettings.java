package prod.tint_wym.novora_backend.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Server-side settings for Novora Accounting ERP push (payroll journals).
 */
@Component
public class AccountingIntegrationSettings {

    private final boolean enabled;
    private final String baseUrl;

    public AccountingIntegrationSettings(
            @Value("${app.accounting.enabled:false}") boolean enabled,
            @Value("${app.accounting.base-url:}") String baseUrl) {
        this.enabled = enabled;
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
    }

    public boolean isReady() {
        return enabled && !baseUrl.isEmpty();
    }

    public String baseUrl() {
        return baseUrl;
    }
}
