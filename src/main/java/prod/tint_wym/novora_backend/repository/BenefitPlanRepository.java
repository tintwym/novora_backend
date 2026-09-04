package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.BenefitPlan;

public interface BenefitPlanRepository extends JpaRepository<BenefitPlan, UUID> {
    List<BenefitPlan> findAllByOrderByNameAsc();

    Optional<BenefitPlan> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
