package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.HelpdeskTicket;

public interface HelpdeskTicketRepository extends JpaRepository<HelpdeskTicket, UUID> {
    List<HelpdeskTicket> findAllByOrderByCreatedAtDesc();

    List<HelpdeskTicket> findAllByRequester_IdOrderByCreatedAtDesc(UUID requesterEmployeeId);

    Optional<HelpdeskTicket> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
