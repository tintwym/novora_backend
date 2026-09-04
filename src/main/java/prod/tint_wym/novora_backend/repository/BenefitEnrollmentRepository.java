package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.BenefitEnrollment;

public interface BenefitEnrollmentRepository extends JpaRepository<BenefitEnrollment, UUID> {
    List<BenefitEnrollment> findAllByOrderByEnrolledAtDesc();

    List<BenefitEnrollment> findAllByEmployee_IdOrderByEnrolledAtDesc(UUID employeeId);
}
