package prod.tint_wym.novora_backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import prod.tint_wym.novora_backend.entity.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Bulk-flip every TRIAL/ACTIVE organization whose trial ended before {@code now} to
     * EXPIRED/READ_ONLY. Returns the row count for logging. Idempotent — re-running it on already
     * flipped orgs is a no-op because the WHERE clause excludes them.
     */
    @Modifying
    @Query(
            "UPDATE Organization o "
                    + "SET o.plan = prod.tint_wym.novora_backend.entity.Organization$Plan.EXPIRED, "
                    + "    o.status = prod.tint_wym.novora_backend.entity.Organization$Status.READ_ONLY, "
                    + "    o.updatedAt = :now "
                    + "WHERE o.plan = prod.tint_wym.novora_backend.entity.Organization$Plan.TRIAL "
                    + "  AND o.status = prod.tint_wym.novora_backend.entity.Organization$Status.ACTIVE "
                    + "  AND o.trialExpiresAt IS NOT NULL "
                    + "  AND o.trialExpiresAt < :now")
    int expireTrialsBefore(@Param("now") LocalDateTime now);
}
