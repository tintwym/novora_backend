package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.TrainingEnrollment;

public interface TrainingEnrollmentRepository extends JpaRepository<TrainingEnrollment, UUID> {
    List<TrainingEnrollment> findAllByTraining_Id(UUID trainingId);

    List<TrainingEnrollment> findAllByEmployee_Id(UUID employeeId);

    Optional<TrainingEnrollment> findByIdAndTraining_Id(UUID id, UUID trainingId);
}
