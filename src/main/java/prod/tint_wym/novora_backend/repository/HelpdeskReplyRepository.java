package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.HelpdeskReply;

public interface HelpdeskReplyRepository extends JpaRepository<HelpdeskReply, UUID> {
    List<HelpdeskReply> findAllByTicket_IdOrderByCreatedAtAsc(UUID ticketId);
}
