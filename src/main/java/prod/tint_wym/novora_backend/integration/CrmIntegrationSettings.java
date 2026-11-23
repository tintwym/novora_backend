package prod.tint_wym.novora_backend.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Server-side settings for the Novora CRM integration. The shared secret never leaves the two
 * backends; per-workspace authorization comes from one-time link codes, not from this secret.
 */
@Component
public class CrmIntegrationSettings {

    private static final int MIN_SECRET_LENGTH = 32;

    private final boolean enabled;
    private final String baseUrl;
    private final String secret;

    public CrmIntegrationSettings(
            @Value("${app.crm.enabled:false}") boolean enabled,
            @Value("${app.crm.base-url:}") String baseUrl,
            @Value("${app.crm.integration-secret:}") String secret) {
        this.enabled = enabled;
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
        this.secret = secret == null ? "" : secret.trim();
    }

    /** True only when the feature is switched on and both a CRM URL and a strong secret are configured. */
    public boolean isReady() {
        return enabled && !baseUrl.isEmpty() && secret.length() >= MIN_SECRET_LENGTH;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String secret() {
        return secret;
    }
}
