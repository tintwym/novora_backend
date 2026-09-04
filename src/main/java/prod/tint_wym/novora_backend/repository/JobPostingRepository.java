package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.JobPosting;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {
    List<JobPosting> findAllByOrderByCreatedAtDesc();

    long countByStatusIgnoreCase(String status);
}
