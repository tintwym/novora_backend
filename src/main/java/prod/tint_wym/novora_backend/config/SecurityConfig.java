package prod.tint_wym.novora_backend.config;

import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import java.util.List;
import org.springframework.http.HttpStatus;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;
import prod.tint_wym.novora_backend.firebase.FirebaseAuthenticationFilter;
import prod.tint_wym.novora_backend.firebase.FirebaseTokenVerifier;
import prod.tint_wym.novora_backend.tenancy.TenantFilter;
import prod.tint_wym.novora_backend.tenancy.TrialExpiryFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * CORS for the React admin SPA (Vercel) and local Next.js dev (different port = cross-origin).
     * Loopback patterns are open for dev. Production origins MUST be configured via
     * {@code APP_CORS_ADDITIONAL_ORIGIN_PATTERNS} as EXACT origins (no wildcards) —
     * any wildcard combined with {@code allowCredentials=true} below is a CSRF bypass.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.additional-origin-patterns:}") String additionalOriginPatterns) {
        CorsConfiguration configuration = new CorsConfiguration();
        var patterns = new ArrayList<String>();
        patterns.addAll(
                List.of(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://[::1]:*",
                        "https://localhost:*",
                        "https://127.0.0.1:*",
                        "https://[::1]:*"));
        if (additionalOriginPatterns != null && !additionalOriginPatterns.isBlank()) {
            for (String raw : additionalOriginPatterns.split(",")) {
                String p = raw.trim();
                if (p.isEmpty()) {
                    continue;
                }
                if (!isSafeOriginPattern(p)) {
                    throw new IllegalStateException(
                            "CORS: wildcard origin patterns are only allowed for loopback hosts " +
                                    "(localhost / 127.0.0.1 / [::1]). Use an exact origin instead of: " + p);
                }
                patterns.add(p);
            }
        }
        configuration.setAllowedOriginPatterns(patterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Accepts:
     *  - any wildcard-free origin string ({@code https://novora-hrms.vercel.app}); CORS+
     *    {@code allowCredentials=true} requires exact origins for non-loopback hosts.
     *  - loopback wildcard patterns ({@code http://localhost:*}, {@code https://[::1]:*}).
     *
     * Rejects anything else, in particular substring tricks the previous naive check missed:
     * {@code https://*.localhost.evil.com}, {@code *://attacker.com?x=localhost},
     * {@code https://attacker.com/?token=127.0.0.1}.
     */
    static boolean isSafeOriginPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) return false;
        if (!pattern.contains("*")) {
            // Exact origin — must still be a valid scheme://host[:port] with no path/query/fragment.
            try {
                var uri = java.net.URI.create(pattern);
                if (uri.getScheme() == null || uri.getHost() == null) return false;
                if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                    return false;
                }
                // Path/query/fragment in an origin pattern is almost always a typo and is not a
                // valid CORS "origin" anyway.
                if (uri.getPath() != null && !uri.getPath().isEmpty()) return false;
                if (uri.getQuery() != null) return false;
                if (uri.getFragment() != null) return false;
                return true;
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        // Wildcard only allowed for loopback hosts, only in the port slot:
        //   http://localhost:*   http://127.0.0.1:*   http://[::1]:*   (+ https variants)
        return pattern.matches("^https?://(localhost|127\\.0\\.0\\.1|\\[::1\\]):\\*$");
    }

    @Bean
    public TenantFilter tenantFilter(AppUserRepository appUserRepository) {
        return new TenantFilter(appUserRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true")
    @ConditionalOnBean(FirebaseTokenVerifier.class)
    public FirebaseAuthenticationFilter firebaseAuthenticationFilter(
            FirebaseTokenVerifier tokenVerifier,
            AppUserRepository appUserRepository) {
        return new FirebaseAuthenticationFilter(tokenVerifier, appUserRepository);
    }

    @Bean
    public TrialExpiryFilter trialExpiryFilter(OrganizationRepository organizationRepository) {
        return new TrialExpiryFilter(organizationRepository);
    }

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            TenantFilter tenantFilter,
            TrialExpiryFilter trialExpiryFilter,
            ObjectProvider<FirebaseAuthenticationFilter> firebaseAuthenticationFilter,
            @Value("${server.servlet.session.cookie.secure:true}") boolean sessionCookieSecure,
            @Value("${app.firebase.enabled:false}") boolean firebaseEnabled
    ) throws Exception {
        // Browser clients read XSRF-TOKEN cookie and send X-XSRF-TOKEN on mutating requests.
        // Use plain token handler: default in Spring Security 6+ is XOR-masking, so cookie value != JSON token
        // and the browser cannot copy the cookie into the header (POSTs get 403).
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokenRepository.setCookiePath("/");
        // Must match session cookie Secure flag: on plain HTTP (local dev), Secure cookies are not stored/sent → 403.
        tokenRepository.setCookieCustomizer(cookie -> cookie.secure(sessionCookieSecure).sameSite("Lax"));
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        // Only skip CSRF for real Firebase Bearer credentials. A spoofed
                        // "Authorization: Bearer …" must NOT bypass CSRF on cookie sessions
                        // (especially when Firebase auth is disabled).
                        .ignoringRequestMatchers(req ->
                                firebaseEnabled && hasNonEmptyBearerToken(req)))
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Optional: serve a built SPA from classpath:/static/ (local unified deploy).
                        // Production UI is hosted on Vercel; route-level auth is enforced in React.
                        .requestMatchers(spaOrStaticRequest()).permitAll()
                        // Keep health unauthenticated for local checks/load balancers.
                        .requestMatchers(req -> {
                            String uri = req.getRequestURI();
                            return uri != null && (uri.equals("/actuator/health") || uri.startsWith("/actuator/health/"));
                        }).permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                // Default entry point for stateful HTTP sessions is 403 Forbidden, which conflates
                // "not logged in" with "logged in but lacking permission". The SPA needs a clean 401
                // on the former so it can boot back to /login without prompting the user to retry.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout
                        .logoutRequestMatcher(logoutRequestMatcher())
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                )
                // Tenant scoping: TenantFilter runs after AuthorizationFilter so the SecurityContext
                // is fully populated; it sets a thread-local that Hibernate's TenantIdResolver reads
                // on every query/persist. TrialExpiryFilter runs after that so the org lookup it
                // does has tenant context already set.
                .addFilterAfter(tenantFilter, AuthorizationFilter.class)
                .addFilterAfter(trialExpiryFilter, TenantFilter.class);

        firebaseAuthenticationFilter.ifAvailable(
                filter -> http.addFilterBefore(filter, AuthorizationFilter.class));

        return http.build();
    }

    private static boolean hasNonEmptyBearerToken(jakarta.servlet.http.HttpServletRequest req) {
        String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return false;
        }
        return !auth.substring("Bearer ".length()).trim().isEmpty();
    }

    private static RequestMatcher logoutRequestMatcher() {
        return request -> {
            if (!HttpMethod.POST.matches(request.getMethod())) {
                return false;
            }
            String uri = request.getRequestURI();
            return "/api/auth/logout".equals(uri) || "/auth/logout".equals(uri);
        };
    }

    private static RequestMatcher spaOrStaticRequest() {
        return request -> {
            String uri = request.getRequestURI();
            if (uri == null) return false;

            // Never treat API/auth/actuator as public static content.
            if (uri.startsWith("/api/") || uri.equals("/api")) return false;
            if (uri.startsWith("/auth/") || uri.equals("/auth")) return false;
            if (uri.startsWith("/actuator/") || uri.equals("/actuator")) return false;

            String method = request.getMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) return false;

            // Static assets (/assets/, favicons, etc.) when a frontend bundle is copied into classpath:/static/.
            if (uri.startsWith("/assets/")) return true;
            if (uri.equals("/") || uri.equals("/index.html")) return true;

            // If this looks like a real file request (has an extension), allow it.
            // Example: /favicon.ico, /manifest.webmanifest, /robots.txt
            if (uri.contains(".") && !uri.endsWith("/")) return true;

            // Extensionless routes are SPA routes (e.g. /login, /dashboard, /admin/approvals).
            // Keep them public so the app can boot and redirect to /login if needed.
            return true;
        };
    }
}

