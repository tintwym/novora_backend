package prod.tint_wym.novora_backend.firebase;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.repository.AppUserRepository;

/**
 * Validates {@code Authorization: Bearer <Firebase ID token>} and populates the
 * {@link SecurityContextHolder}. When no Bearer header is present, the filter is a no-op so
 * legacy cookie sessions continue to work (tests, bootstrap admin).
 */
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseTokenVerifier tokenVerifier;
    private final AppUserRepository appUserRepository;

    public FirebaseAuthenticationFilter(
            FirebaseTokenVerifier tokenVerifier, AppUserRepository appUserRepository) {
        this.tokenVerifier = tokenVerifier;
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        String idToken = header.substring(BEARER_PREFIX.length()).trim();
        if (idToken.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            FirebaseToken decoded = tokenVerifier.verify(idToken);
            String uid = decoded.getUid();
            String email = normalizeEmail(decoded.getEmail());
            if (!decoded.isEmailVerified()) {
                writeUnauthorized(response, "Firebase email must be verified");
                return;
            }

            if (email != null) {
                Optional<AppUser> byEmail = appUserRepository.findByEmail(email);
                if (byEmail.isPresent()) {
                    String existingUid = byEmail.get().getFirebaseUid();
                    if (existingUid != null && !existingUid.isBlank() && !existingUid.equals(uid)) {
                        writeUnauthorized(response, "Firebase account does not match linked user");
                        return;
                    }
                }
            }

            Optional<AppUser> linked = resolveLinkedUser(uid, email);

            FirebaseAuthenticationToken auth;
            if (linked.isPresent()) {
                AppUser user = linked.get();
                if (!user.isActive()) {
                    writeUnauthorized(response, "Account is disabled");
                    return;
                }
                String role = user.getRole() == null ? "EMPLOYEE" : user.getRole();
                var authority = new SimpleGrantedAuthority(
                        role.startsWith("ROLE_") ? role : "ROLE_" + role);
                auth = FirebaseAuthenticationToken.provisioned(
                        uid, user.getEmail(), user.getId(), java.util.List.of(authority));
            } else {
                auth = FirebaseAuthenticationToken.unprovisioned(uid, email);
            }
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (FirebaseAuthException ex) {
            LOG.debug("Firebase token rejected: {}", ex.getMessage());
            writeUnauthorized(response, "Invalid or expired Firebase token");
        }
    }

    /**
     * Resolve the AppUser for a verified Firebase token. Primary lookup is by UID; a one-time
     * link by email is allowed only when the DB row has no firebase_uid yet (legacy password
     * account upgrading to Firebase).
     */
    private Optional<AppUser> resolveLinkedUser(String uid, String email) {
        Optional<AppUser> byUid = appUserRepository.findByFirebaseUid(uid);
        if (byUid.isPresent()) {
            return byUid;
        }
        if (email == null) {
            return Optional.empty();
        }
        Optional<AppUser> byEmail = appUserRepository.findByEmail(email);
        if (byEmail.isEmpty()) {
            return Optional.empty();
        }
        AppUser user = byEmail.get();
        String existingUid = user.getFirebaseUid();
        if (existingUid != null && !existingUid.isBlank() && !existingUid.equals(uid)) {
            return Optional.empty();
        }
        if (existingUid == null || existingUid.isBlank()) {
            user.setFirebaseUid(uid);
            user.setEmailVerified(true);
            user.setUpdatedAt(LocalDateTime.now());
            return Optional.of(appUserRepository.save(user));
        }
        return Optional.of(user);
    }

    private static void writeUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String escaped = message.replace("\"", "\\\"");
        response.getWriter().write("{\"message\":\"" + escaped + "\"}");
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        String t = email.trim().toLowerCase();
        return t.isEmpty() ? null : t;
    }
}
