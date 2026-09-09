package prod.tint_wym.novora_backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.RosterEntry;

public interface RosterEntryRepository extends JpaRepository<RosterEntry, UUID> {
    List<RosterEntry> findAllByOrderByWorkDateDesc();

    List<RosterEntry> findAllByWorkDateBetweenOrderByWorkDateAsc(LocalDate from, LocalDate to);

    Optional<RosterEntry> findByEmployee_IdAndWorkDate(UUID employeeId, LocalDate workDate);

    List<RosterEntry> findAllByOrganizationIdAndWorkDate(UUID organizationId, LocalDate workDate);
}
