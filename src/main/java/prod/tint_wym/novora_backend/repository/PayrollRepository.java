package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.Payroll;

public interface PayrollRepository extends JpaRepository<Payroll, UUID> {

    List<Payroll> findAllByPayYearAndPayMonthOrderByEmployee_FirstNameAsc(int payYear, int payMonth);

    List<Payroll> findAllByEmployee_IdOrderByPayYearDescPayMonthDesc(UUID employeeId);

    Optional<Payroll> findByEmployee_IdAndPayYearAndPayMonth(UUID employeeId, int payYear, int payMonth);

    Optional<Payroll> findByIdAndEmployee_OrganizationId(UUID id, UUID organizationId);

    long countByPayYearAndPayMonth(int payYear, int payMonth);
}
