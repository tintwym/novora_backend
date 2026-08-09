package prod.tint_wym.novora_backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SpaForwardController {

    /**
     * Forwards extensionless GET routes to the SPA's {@code /index.html} so deep links like
     * {@code /dashboard} or {@code /employees} render the React SPA when static files are
     * served from this JAR (optional local deploy). Production uses Vercel for the UI.
     *
     * <p>The previous implementation re-forwarded API/auth/actuator URIs back to themselves
     * (e.g. {@code forward:/api/auth/me}) whenever the controller for that path was missing.
     * Spring's DispatcherServlet then re-entered this controller for the forwarded request,
     * producing either a 500 or a stack overflow. Unmapped API paths now return a clean 404
     * via {@link ResponseStatusException} instead of looping.
     */
    @GetMapping({"/{path:[^\\.]*}", "/**/{path:[^\\.]*}"})
    public String forwardSpaRoutes(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return "forward:/index.html";

        // API/auth/actuator paths must be served by their own controllers. If we got here
        // for one of those, the path is simply not mapped — return 404 so the caller knows.
        if (uri.startsWith("/api/") || uri.equals("/api")
                || uri.startsWith("/auth/") || uri.equals("/auth")
                || uri.startsWith("/actuator/") || uri.equals("/actuator")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return "forward:/index.html";
    }
}

