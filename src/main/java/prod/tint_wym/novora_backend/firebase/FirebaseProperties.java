package prod.tint_wym.novora_backend.firebase;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Admin SDK settings. When {@link #enabled()} is false (default in tests),
 * the API keeps using cookie sessions; Bearer tokens are ignored.
 */
@ConfigurationProperties(prefix = "app.firebase")
public record FirebaseProperties(boolean enabled, String serviceAccountJson) {

    public FirebaseProperties {
        if (serviceAccountJson == null) {
            serviceAccountJson = "";
        }
    }
}
