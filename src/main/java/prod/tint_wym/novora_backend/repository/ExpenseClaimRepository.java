package prod.tint_wym.novora_backend.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import prod.tint_wym.novora_backend.entity.ExpenseClaim;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> {

    List<ExpenseClaim> findAllByEmployee_IdOrderByCreatedAtDesc(UUID employeeId);

    List<ExpenseClaim> findAllByStatusIgnoreCaseOrderByCreatedAtDesc(String status);

    List<ExpenseClaim> findAllByOrderByCreatedAtDesc();

    Optional<ExpenseClaim> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByStatusIgnoreCase(String status);

    @Query("""
            SELECT COALESCE(SUM(c.amount), 0) FROM ExpenseClaim c
            WHERE c.employee.id = :employeeId
              AND LOWER(c.category) = LOWER(:category)
              AND c.claimDate = :claimDate
              AND LOWER(c.status) IN ('pending', 'approved')
            """)
    BigDecimal sumForEmployeeCategoryOnDate(
            @Param("employeeId") UUID employeeId,
            @Param("category") String category,
            @Param("claimDate") LocalDate claimDate);

    @Query("""
            SELECT COALESCE(SUM(c.amount), 0) FROM ExpenseClaim c
            WHERE c.employee.id = :employeeId
              AND LOWER(c.category) = LOWER(:category)
              AND c.claimDate >= :from
              AND c.claimDate <= :to
              AND LOWER(c.status) IN ('pending', 'approved')
            """)
    BigDecimal sumForEmployeeCategoryInRange(
            @Param("employeeId") UUID employeeId,
            @Param("category") String category,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
