package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import prod.tint_wym.novora_backend.entity.LeaveRequest;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    List<LeaveRequest> findAllByEmployee_IdOrderByCreatedAtDesc(UUID employeeId);

    List<LeaveRequest> findAllByStatusOrderByCreatedAtDesc(String status);

    Optional<LeaveRequest> findByIdAndEmployee_Id(UUID id, UUID employeeId);

    @Query("""
            SELECT lr FROM LeaveRequest lr JOIN lr.employee e
            WHERE LOWER(lr.status) = LOWER(:status) AND e.organizationId = :orgId
            ORDER BY lr.createdAt DESC
            """)
    List<LeaveRequest> findAllByStatusAndEmployee_OrganizationIdOrderByCreatedAtDesc(
            @Param("status") String status, @Param("orgId") UUID orgId);

    @Query("""
            SELECT lr FROM LeaveRequest lr JOIN lr.employee e
            WHERE lr.id = :id AND e.organizationId = :orgId
            """)
    Optional<LeaveRequest> findByIdAndEmployee_OrganizationId(@Param("id") UUID id, @Param("orgId") UUID orgId);

    @Query("""
            SELECT COUNT(lr) > 0 FROM LeaveRequest lr
            WHERE lr.employee.id = :employeeId
              AND LOWER(lr.status) IN ('pending', 'approved')
              AND lr.startDate <= :endDate
              AND lr.endDate >= :startDate
            """)
    boolean existsOverlappingLeave(
            @Param("employeeId") UUID employeeId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate);

    @Query("""
            SELECT COUNT(lr) FROM LeaveRequest lr JOIN lr.employee e
            WHERE LOWER(lr.status) = LOWER(:status) AND e.organizationId = :orgId
            """)
    long countByStatusAndEmployee_OrganizationId(@Param("status") String status, @Param("orgId") UUID orgId);
}
