package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.Position;

public interface PositionRepository extends JpaRepository<Position, UUID> {
    Optional<Position> findFirstByDepartment_IdOrderByTitleAsc(UUID departmentId);

    List<Position> findByDepartment_Id(UUID departmentId);
}
