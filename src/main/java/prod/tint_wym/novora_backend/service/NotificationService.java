package prod.tint_wym.novora_backend.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.NotificationDtos;
import prod.tint_wym.novora_backend.entity.AppUser;
import prod.tint_wym.novora_backend.entity.Notification;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.repository.NotificationRepository;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;

    public NotificationService(
            NotificationRepository notificationRepository, AppUserRepository appUserRepository) {
        this.notificationRepository = notificationRepository;
        this.appUserRepository = appUserRepository;
    }

    private AppUser requireUserByEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.US);
        return appUserRepository
                .findByEmail(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZONE).toInstant();
    }

    private NotificationDtos.NotificationResponse toResponse(Notification n) {
        return new NotificationDtos.NotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.getType(),
                n.isRead(),
                toInstant(n.getReadAt()),
                toInstant(n.getCreatedAt()));
    }

    public List<NotificationDtos.NotificationResponse> myNotifications(String email) {
        AppUser user = requireUserByEmail(email);
        return notificationRepository.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationDtos.NotificationResponse markRead(String email, UUID notificationId) {
        AppUser user = requireUserByEmail(email);
        Notification n = notificationRepository
                .findByIdAndUser_Id(notificationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
            n = notificationRepository.save(n);
        }
        return toResponse(n);
    }

    /**
     * Best-effort notification create. Failures are logged and swallowed so callers (leave/claim
     * decisions) are never blocked by notification side effects. Uses REQUIRES_NEW so a failed
     * insert cannot mark the caller's transaction rollback-only.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(UUID userId, String title, String message, String type) {
        try {
            if (userId == null) {
                return;
            }
            AppUser user = appUserRepository.findById(userId).orElse(null);
            if (user == null) {
                return;
            }
            String normalizedType =
                    type == null || type.isBlank() ? "system" : type.trim().toLowerCase(Locale.US);
            Notification n = new Notification();
            n.setUser(user);
            n.setTitle(title == null ? "" : title.trim());
            n.setMessage(message);
            n.setType(normalizedType);
            n.setRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(n);
        } catch (Exception ex) {
            LOG.warn("Failed to create notification for user {}: {}", userId, ex.getMessage());
        }
    }
}
