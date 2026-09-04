package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.JobOffer;

public interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {
    List<JobOffer> findAllByOrderByCreatedAtDesc();

    Optional<JobOffer> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
