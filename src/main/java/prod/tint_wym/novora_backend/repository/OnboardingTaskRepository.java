package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.OnboardingTask;

public interface OnboardingTaskRepository extends JpaRepository<OnboardingTask, UUID> {
    List<OnboardingTask> findAllByEmployee_IdOrderBySortOrderAscCreatedAtAsc(UUID employeeId);

    List<OnboardingTask> findAllByOrderByCreatedAtDesc();

    Optional<OnboardingTask> findByIdAndEmployee_Id(UUID id, UUID employeeId);

    long countByEmployee_IdAndStatusIgnoreCase(UUID employeeId, String status);
}
