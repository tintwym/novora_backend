package prod.tint_wym.novora_backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import prod.tint_wym.novora_backend.entity.Attendance;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    List<Attendance> findAllByEmployee_IdOrderByWorkDateDesc(UUID employeeId);

    List<Attendance> findAllByOrderByWorkDateDesc();

    Optional<Attendance> findByEmployee_IdAndWorkDate(UUID employeeId, LocalDate workDate);

    @Query("""
            SELECT a FROM Attendance a JOIN a.employee e
            WHERE e.organizationId = :orgId
              AND (:date IS NULL OR a.workDate = :date)
            ORDER BY a.workDate DESC
            """)
    List<Attendance> findAllByEmployee_OrganizationIdAndOptionalDate(
            @Param("orgId") UUID orgId, @Param("date") LocalDate date);

    List<Attendance> findAllByEmployee_OrganizationIdAndWorkDate(UUID organizationId, LocalDate workDate);
}
