package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.EmployeeEducation;

public interface EmployeeEducationRepository extends JpaRepository<EmployeeEducation, UUID> {
    List<EmployeeEducation> findAllByEmployee_IdOrderByCreatedAtDesc(UUID employeeId);

    Optional<EmployeeEducation> findByIdAndEmployee_Id(UUID id, UUID employeeId);
}
