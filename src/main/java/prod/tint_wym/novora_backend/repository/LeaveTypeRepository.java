package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.LeaveType;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {
    Optional<LeaveType> findByNameIgnoreCase(String name);

    Optional<LeaveType> findByCodeIgnoreCase(String code);

    List<LeaveType> findAllByOrderBySortOrderAscNameAsc();

    List<LeaveType> findAllByActiveTrueOrderBySortOrderAscNameAsc();
}
