package prod.tint_wym.novora_backend.dto;

import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record NotificationResponse(
            UUID id,
            String title,
            String message,
            String type,
            boolean read,
            Instant readAt,
            Instant createdAt
    ) {
    }
}
