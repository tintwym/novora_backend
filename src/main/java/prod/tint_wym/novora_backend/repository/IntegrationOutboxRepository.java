package prod.tint_wym.novora_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import prod.tint_wym.novora_backend.entity.IntegrationOutboxEvent;

public interface IntegrationOutboxRepository extends JpaRepository<IntegrationOutboxEvent, UUID> {

    @Query("SELECT e FROM IntegrationOutboxEvent e WHERE e.deliveredAt IS NULL AND e.nextAttemptAt <= :now "
            + "ORDER BY e.createdAt ASC")
    List<IntegrationOutboxEvent> findDue(@Param("now") LocalDateTime now, Pageable page);

    long countByOrganizationIdAndDeliveredAtIsNull(UUID organizationId);

    /** Drops undelivered events when a workspace disconnects, so they aren't sent to a CRM that no longer expects them. */
    @Modifying
    @Query("DELETE FROM IntegrationOutboxEvent e WHERE e.organizationId = :orgId AND e.deliveredAt IS NULL")
    int deletePending(@Param("orgId") UUID organizationId);
}
