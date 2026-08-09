package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.LeaveBalance;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    Optional<LeaveBalance> findByEmployee_IdAndLeaveType_IdAndBalanceYear(
            UUID employeeId, UUID leaveTypeId, int balanceYear);

    List<LeaveBalance> findAllByEmployee_IdAndBalanceYear(UUID employeeId, int balanceYear);
}
