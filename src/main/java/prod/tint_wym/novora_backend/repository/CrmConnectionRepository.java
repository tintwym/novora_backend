package prod.tint_wym.novora_backend.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.CrmConnection;

public interface CrmConnectionRepository extends JpaRepository<CrmConnection, UUID> {

    Optional<CrmConnection> findByOrganizationId(UUID organizationId);
}
