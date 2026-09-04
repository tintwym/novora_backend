package prod.tint_wym.novora_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<Notification> findByIdAndUser_Id(UUID id, UUID userId);
}
