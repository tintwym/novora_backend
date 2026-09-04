package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.ShiftPattern;

public interface ShiftPatternRepository extends JpaRepository<ShiftPattern, UUID> {
    List<ShiftPattern> findAllByOrderByNameAsc();
}
