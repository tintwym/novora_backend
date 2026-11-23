package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.ClaimCategoryLimit;

public interface ClaimCategoryLimitRepository extends JpaRepository<ClaimCategoryLimit, UUID> {

    List<ClaimCategoryLimit> findAllByOrganizationIdOrderByCategoryAsc(UUID organizationId);

    Optional<ClaimCategoryLimit> findByOrganizationIdAndCategoryIgnoreCase(UUID organizationId, String category);
}
