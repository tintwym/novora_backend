package prod.tint_wym.novora_backend.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.repository.AppUserRepository;

/**
 * Populates {@link TenantContext} for the lifetime of the request from the authenticated caller's
 * {@code organization_id}. Hibernate's {@link TenantIdResolver} then forwards that value into
 * every query / persist as the {@code @TenantId} discriminator, so service code never has to think
 * about tenant scoping.
 *
 * <p>This filter is wired into the Spring Security chain (see {@code SecurityConfig}) <em>after</em>
 * the security context has been restored, so {@link SecurityContextHolder} is already populated.
 * Anonymous endpoints (login, register, csrf, health) leave the context empty — Hibernate will
 * skip the tenant predicate, which is exactly what login's global {@code findByEmail} needs.
 */
public class TenantFilter extends OncePerRequestFilter {

    private final AppUserRepository appUserRepository;

    public TenantFilter(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<AppUser> caller = resolveCaller();
        if (caller.isPresent() && !caller.get().isActive()) {
            // Soft-deactivated accounts must not keep using an existing cookie session.
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Account is disabled\"}");
            return;
        }
        UUID orgId = caller.isPresent() ? caller.get().getOrganizationId() : null;
        if (orgId != null) {
            TenantContext.set(orgId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Optional<AppUser> resolveCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        String principalName = auth.getName();
        if (principalName == null || principalName.isBlank() || "anonymousUser".equals(principalName)) {
            return Optional.empty();
        }
        return appUserRepository.findByEmail(principalName.trim().toLowerCase());
    }
}
