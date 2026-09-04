package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByAppUser_EmailIgnoreCase(String email);

    Optional<Employee> findByEmployeeCodeIgnoreCase(String employeeCode);

    /** Active roster — soft-deleted (status='terminated') rows are excluded by default. */
    List<Employee> findAllByStatusNotIgnoreCase(String excludedStatus);

    Optional<Employee> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByStatusNotIgnoreCase(String excludedStatus);
}
