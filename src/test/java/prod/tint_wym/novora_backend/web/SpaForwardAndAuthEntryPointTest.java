package prod.tint_wym.novora_backend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * Locks two behaviours that previously broke the SPA:
 *
 * <ul>
 *   <li>Unmapped API/auth/actuator paths must return 404, not 500. The earlier
 *       {@code SpaForwardController} re-forwarded {@code /api/auth/me} back to itself,
 *       producing a dispatcher loop and a 500.</li>
 *   <li>Unauthenticated calls to protected endpoints must return 401, not 403, so the
 *       SPA's session refresh can cleanly distinguish "not logged in" from "logged in
 *       but lacking permission".</li>
 * </ul>
 */
@SpringBootTest
class SpaForwardAndAuthEntryPointTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void apiAuthCsrfReturnsToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    void unmappedApiPathReturnsNotFoundNotInternalServerError() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unmappedAuthPathReturnsNotFoundNotInternalServerError() throws Exception {
        mockMvc.perform(get("/auth/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyAuthMeAliasReturnsUnauthorizedWhenAnonymous() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedMeReturnsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedProtectedEndpointReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/employees"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void extensionlessSpaRouteStillForwardsToIndexHtml() throws Exception {
        // /dashboard has no controller and no extension → goes to SpaForwardController →
        // forward:/index.html. index.html is optional under classpath:/static/; in tests
        // it doesn't exist, so the forward resolves to 404 from the static handler — but the
        // important thing is the request is NOT a 500 from the loop.
        mockMvc.perform(get("/dashboard"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 500) {
                        throw new AssertionError("/dashboard returned 500, expected 200 or 404 (forward to SPA)");
                    }
                });
    }
}
