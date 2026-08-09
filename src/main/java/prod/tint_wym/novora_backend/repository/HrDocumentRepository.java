package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.HrDocument;

public interface HrDocumentRepository extends JpaRepository<HrDocument, UUID> {
    List<HrDocument> findAllByEmployee_IdOrderByUploadedAtDesc(UUID employeeId);

    Optional<HrDocument> findByIdAndEmployee_Id(UUID id, UUID employeeId);
}
