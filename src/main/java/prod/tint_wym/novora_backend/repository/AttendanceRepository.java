package prod.tint_wym.novora_backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.Attendance;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    List<Attendance> findAllByEmployee_IdOrderByWorkDateDesc(UUID employeeId);

    Optional<Attendance> findByEmployee_IdAndWorkDate(UUID employeeId, LocalDate workDate);
}
