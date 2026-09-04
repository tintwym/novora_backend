package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {
    List<Holiday> findAllByOrderByHolidayDateAsc();
}
