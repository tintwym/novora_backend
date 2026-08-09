package prod.tint_wym.novora_backend.tenancy;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Hibernate consults this on every session open / transaction begin to figure out which tenant the
 * caller belongs to. We forward to {@link TenantContext}, which is populated by {@code TenantFilter}
 * after Spring Security has resolved the principal.
 *
 * <p>Hibernate 7 requires a non-null return value when {@code @TenantId} is present on any entity
 * (it asserts this when opening a session). For requests that genuinely have no tenant — anonymous
 * endpoints (login, register, csrf, health), background sessions opened by Spring Data JPA at
 * startup for query introspection, the {@code @Scheduled} sweeper, etc. — we return a fixed
 * "no-tenant" sentinel UUID. The sentinel doesn't match any real organization id, so queries on
 * {@code @TenantId} entities run with the predicate {@code organization_id = '00000000-…'} and
 * return nothing, which is the conservative default.
 *
 * <p>The login flow's {@code AppUserRepository.findByEmail} is unaffected because {@code AppUser}
 * is intentionally <strong>not</strong> annotated with {@code @TenantId}.
 */
@Component
public class TenantIdResolver implements CurrentTenantIdentifierResolver<UUID> {

    /** Marker for "no tenant in scope". Picked so it can never collide with a real {@link UUID}. */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID current = TenantContext.get();
        return current != null ? current : NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
