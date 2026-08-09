package prod.tint_wym.novora_backend.tenancy;

import java.util.UUID;

/**
 * Per-thread holder for the current request's organization id. Populated by {@code TenantFilter}
 * after authentication and cleared in a {@code finally} block at the end of the request to keep
 * thread-pool reuse clean.
 *
 * <p>Use a thread-local instead of a request-scoped Spring bean so non-controller code paths
 * (e.g. {@code @Async} jobs spawned from a request, scheduled tasks) can also read the value
 * without forcing every collaborator to take a request-scoped proxy. Background jobs that need
 * to act for a specific tenant must {@link #set(UUID)} explicitly.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID organizationId) {
        if (organizationId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(organizationId);
        }
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
