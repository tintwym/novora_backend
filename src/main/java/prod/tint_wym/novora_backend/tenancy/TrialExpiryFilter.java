package prod.tint_wym.novora_backend.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;
import prod.tint_wym.novora_backend.entity.Organization;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;

/**
 * Returns 402 Payment Required for write requests when the caller's org is in READ_ONLY status
 * (i.e., its trial expired). GETs always pass through so the user can still read their data and
 * navigate to the upgrade page. Auth, CSRF, and actuator paths are exempt so a locked-out user
 * can still log out and the load balancer can still health-check.
 *
 * <p>Wired into the Spring Security chain (see {@code SecurityConfig}) after {@code TenantFilter}
 * so {@link TenantContext} is already populated. Not a {@code @Component} on purpose — that would
 * cause Spring Boot to also register it as a top-level servlet filter, where it would run
 * <em>before</em> the security chain populates the auth context.
 */
public class TrialExpiryFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final OrganizationRepository organizationRepository;

    public TrialExpiryFilter(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (isExempt(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            // Unauthenticated request — Security will deal with it. Don't preempt 401 with 402.
            chain.doFilter(request, response);
            return;
        }
        Organization org = organizationRepository.findById(orgId).orElse(null);
        if (org == null) {
            chain.doFilter(request, response);
            return;
        }
        if (org.getStatus() == Organization.Status.SUSPENDED) {
            writeJson(response, 403, "{\"code\":\"workspace_suspended\","
                    + "\"message\":\"This workspace is suspended. Contact support.\"}");
            return;
        }
        // Enforce trial end immediately — do not wait for the hourly sweeper to flip status.
        if (org.getStatus() == Organization.Status.READ_ONLY
                || org.getPlan() == Organization.Plan.EXPIRED
                || isTrialPastExpiry(org)) {
            writeJson(response, 402, "{\"code\":\"trial_expired\","
                    + "\"message\":\"Your free trial has ended. Upgrade your plan to continue.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isTrialPastExpiry(Organization org) {
        if (org.getPlan() != Organization.Plan.TRIAL) {
            return false;
        }
        LocalDateTime expiresAt = org.getTrialExpiresAt();
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * Endpoints that must keep working even when the org is read-only so the user can still log
     * out, refresh CSRF, hit /me, or change their plan in Settings (Phase 2 will route Stripe
     * Checkout through here too).
     */
    private static boolean isExempt(String uri) {
        if (uri == null) return false;
        return uri.startsWith("/api/auth/")
                || uri.startsWith("/auth/")
                || uri.startsWith("/actuator/")
                || uri.startsWith("/api/billing/"); // reserved for Phase 2 Stripe webhooks
    }

    private static void writeJson(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
    }
}
