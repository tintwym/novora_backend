package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.TimeLog;

public interface TimeLogRepository extends JpaRepository<TimeLog, UUID> {
    List<TimeLog> findAllByEmployee_IdOrderByWorkDateDescCreatedAtDesc(UUID employeeId);
}
