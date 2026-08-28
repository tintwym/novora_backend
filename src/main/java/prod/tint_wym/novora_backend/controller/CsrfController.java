package prod.tint_wym.novora_backend.controller;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {

    /**
     * CSRF metadata for the React admin SPA (cookie session + {@code X-XSRF-TOKEN} header).
     *
     * <p>The token is returned in the JSON body because some proxies and dev setups make it easier
     * for the client to bootstrap from the response; the browser also receives {@code XSRF-TOKEN}
     * as a readable cookie ({@link
     * org.springframework.security.web.csrf.CookieCsrfTokenRepository#withHttpOnlyFalse()}), which
     * the web client reads via {@code document.cookie} when present.
     *
     * <p>Safe with credentials only when CORS is locked to exact production origins (see {@code
     * application.properties}).
     */
    @GetMapping({"/api/auth/csrf", "/auth/csrf"})
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getToken()
        );
    }
}
