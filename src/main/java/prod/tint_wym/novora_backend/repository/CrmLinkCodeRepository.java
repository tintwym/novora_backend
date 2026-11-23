package prod.tint_wym.novora_backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import prod.tint_wym.novora_backend.entity.CrmLinkCode;

public interface CrmLinkCodeRepository extends JpaRepository<CrmLinkCode, UUID> {

    Optional<CrmLinkCode> findByCodeHash(String codeHash);

    /** Retires any code still outstanding for the workspace so only the newest one works. */
    @Modifying
    @Query("UPDATE CrmLinkCode c SET c.usedAt = :now WHERE c.organizationId = :orgId AND c.usedAt IS NULL")
    int retireOpenCodes(@Param("orgId") UUID organizationId, @Param("now") LocalDateTime now);
}
