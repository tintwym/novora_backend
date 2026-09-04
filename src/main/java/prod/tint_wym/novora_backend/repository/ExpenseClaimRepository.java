package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.ExpenseClaim;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> {

    List<ExpenseClaim> findAllByEmployee_IdOrderByCreatedAtDesc(UUID employeeId);

    List<ExpenseClaim> findAllByStatusIgnoreCaseOrderByCreatedAtDesc(String status);

    List<ExpenseClaim> findAllByOrderByCreatedAtDesc();

    Optional<ExpenseClaim> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByStatusIgnoreCase(String status);
}
