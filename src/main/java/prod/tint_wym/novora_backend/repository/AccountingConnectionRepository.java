package prod.tint_wym.novora_backend.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.AccountingConnection;

public interface AccountingConnectionRepository extends JpaRepository<AccountingConnection, UUID> {

    Optional<AccountingConnection> findByOrganizationId(UUID organizationId);
}
