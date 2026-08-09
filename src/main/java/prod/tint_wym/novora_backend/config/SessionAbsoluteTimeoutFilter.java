package prod.tint_wym.novora_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hard-caps authenticated HTTP sessions at 1 hour from creation, even if the user stays active.
 * Complements {@code server.servlet.session.timeout=1h} (idle timeout).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SessionAbsoluteTimeoutFilter extends OncePerRequestFilter {

    public static final Duration MAX_SESSION_AGE = Duration.ofHours(1);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            long ageMs = System.currentTimeMillis() - session.getCreationTime();
            if (ageMs >= MAX_SESSION_AGE.toMillis()) {
                try {
                    session.invalidate();
                } catch (IllegalStateException ignored) {
                    // already invalidated
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
