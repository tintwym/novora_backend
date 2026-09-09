package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.DepositType;

public interface DepositTypeRepository extends JpaRepository<DepositType, UUID> {
    List<DepositType> findAllByOrderByNameAsc();
}
