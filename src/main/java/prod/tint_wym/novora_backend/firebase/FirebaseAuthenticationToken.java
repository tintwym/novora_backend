package prod.tint_wym.novora_backend.firebase;

import java.util.Collection;
import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Spring Security principal for a verified Firebase ID token.
 *
 * <p>Two shapes:
 * <ul>
 *   <li><strong>Provisioned</strong> — {@code appUserId != null}: the Firebase identity is linked
 *       to a row in {@code users}. Used for normal API calls after workspace signup.</li>
 *   <li><strong>Unprovisioned</strong> — {@code appUserId == null}: Firebase identity is valid but
 *       the caller has not completed {@code POST /auth/firebase/register} yet. Only allowed on
 *       that endpoint.</li>
 * </ul>
 */
public class FirebaseAuthenticationToken extends AbstractAuthenticationToken {

    private final String firebaseUid;
    private final String email;
    private final java.util.UUID appUserId;

    private FirebaseAuthenticationToken(
            String firebaseUid,
            String email,
            java.util.UUID appUserId,
            Collection<? extends GrantedAuthority> authorities,
            boolean authenticated) {
        super(authorities);
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.appUserId = appUserId;
        setAuthenticated(authenticated);
    }

    public static FirebaseAuthenticationToken provisioned(
            String firebaseUid,
            String email,
            java.util.UUID appUserId,
            Collection<? extends GrantedAuthority> authorities) {
        return new FirebaseAuthenticationToken(firebaseUid, email, appUserId, authorities, true);
    }

    public static FirebaseAuthenticationToken unprovisioned(String firebaseUid, String email) {
        return new FirebaseAuthenticationToken(
                firebaseUid, email, null, Collections.emptyList(), true);
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getEmail() {
        return email;
    }

    public java.util.UUID getAppUserId() {
        return appUserId;
    }

    public boolean isProvisioned() {
        return appUserId != null;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return email != null ? email : firebaseUid;
    }
}
