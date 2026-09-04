package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.EmployeeFamily;

public interface EmployeeFamilyRepository extends JpaRepository<EmployeeFamily, UUID> {
    List<EmployeeFamily> findAllByEmployee_IdOrderByCreatedAtDesc(UUID employeeId);

    Optional<EmployeeFamily> findByIdAndEmployee_Id(UUID id, UUID employeeId);
}
