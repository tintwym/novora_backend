package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import prod.tint_wym.novora_backend.entity.PerformanceReview;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {
    @Query("""
            SELECT pr FROM PerformanceReview pr JOIN pr.employee e
            WHERE e.organizationId = :orgId
            ORDER BY pr.createdAt DESC
            """)
    List<PerformanceReview> findAllByEmployee_OrganizationIdOrderByCreatedAtDesc(@Param("orgId") UUID orgId);
}
