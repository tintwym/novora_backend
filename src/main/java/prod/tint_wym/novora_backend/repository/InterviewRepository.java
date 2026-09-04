package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.Interview;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {
    List<Interview> findAllByOrderByScheduledAtDesc();
}
