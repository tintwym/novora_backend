package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Looks up a user globally across every organization. Used by login and TenantFilter, both of
     * which run before a tenant is known. Other callers should prefer
     * {@link #findAllByOrganizationId(UUID)} or {@link #findByIdAndOrganizationId(UUID, UUID)}.
     */
    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByFirebaseUid(String firebaseUid);

    /**
     * Org-scoped variant for admin endpoints. AppUser intentionally lacks {@code @TenantId} so
     * login can find users globally; admin queries must therefore filter explicitly to avoid
     * leaking other tenants' user lists.
     */
    List<AppUser> findAllByOrganizationId(UUID organizationId);

    Optional<AppUser> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
