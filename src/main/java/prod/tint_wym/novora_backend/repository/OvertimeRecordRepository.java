package prod.tint_wym.novora_backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import prod.tint_wym.novora_backend.entity.OvertimeRecord;

public interface OvertimeRecordRepository extends JpaRepository<OvertimeRecord, UUID> {
    List<OvertimeRecord> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT o FROM OvertimeRecord o
            WHERE o.organizationId = :orgId
              AND o.workDate >= :from
              AND o.workDate <= :to
              AND (:status IS NULL OR LOWER(o.status) = LOWER(:status))
            ORDER BY o.createdAt DESC
            """)
    List<OvertimeRecord> findAllFiltered(
            @Param("orgId") UUID orgId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") String status);
}
