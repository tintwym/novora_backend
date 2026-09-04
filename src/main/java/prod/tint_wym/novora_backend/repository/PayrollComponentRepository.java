package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.PayrollComponent;

public interface PayrollComponentRepository extends JpaRepository<PayrollComponent, UUID> {
    List<PayrollComponent> findAllByPayroll_Id(UUID payrollId);
}
