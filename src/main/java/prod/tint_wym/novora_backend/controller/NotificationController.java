package prod.tint_wym.novora_backend.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.NotificationDtos;
import prod.tint_wym.novora_backend.service.NotificationService;

@RestController
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/my/notifications")
    public List<NotificationDtos.NotificationResponse> myNotifications(Authentication auth) {
        return notificationService.myNotifications(auth.getName());
    }

    @PostMapping("/api/my/notifications/{id}/read")
    public NotificationDtos.NotificationResponse markRead(Authentication auth, @PathVariable UUID id) {
        return notificationService.markRead(auth.getName(), id);
    }
}
