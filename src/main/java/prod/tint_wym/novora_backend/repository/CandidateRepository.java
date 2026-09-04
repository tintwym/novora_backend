package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.Candidate;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {
    List<Candidate> findAllByOrderByAppliedAtDesc();

    List<Candidate> findAllByJobPosting_IdOrderByAppliedAtDesc(UUID jobPostingId);

    long countByJobPosting_Id(UUID jobPostingId);
}
