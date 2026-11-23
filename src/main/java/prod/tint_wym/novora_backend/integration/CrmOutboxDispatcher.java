package prod.tint_wym.novora_backend.integration;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import prod.tint_wym.novora_backend.entity.CrmConnection;
import prod.tint_wym.novora_backend.entity.IntegrationOutboxEvent;
import prod.tint_wym.novora_backend.repository.CrmConnectionRepository;
import prod.tint_wym.novora_backend.repository.IntegrationOutboxRepository;

/**
 * Delivers queued employee changes to Novora CRM, retrying with growing delays until the CRM
 * confirms receipt. The CRM may be asleep (Render free tier) or redeploying, so a failed delivery
 * is normal and never loses the event; the CRM's own startup reconcile is the second safety net.
 */
@Component
public class CrmOutboxDispatcher {

    static final String EVENTS_PATH = "/api/v1/integrations/hrms/events";
    static final int MAX_ATTEMPTS = 20;

    private static final Logger LOG = LoggerFactory.getLogger(CrmOutboxDispatcher.class);
    private static final int BATCH_SIZE = 25;
    private static final long[] BACKOFF_MINUTES = {1, 5, 15, 60, 180, 360};

    private final CrmIntegrationSettings settings;
    private final IntegrationOutboxRepository outbox;
    private final CrmConnectionRepository connections;
    private final RestClient restClient;

    public CrmOutboxDispatcher(
            CrmIntegrationSettings settings,
            IntegrationOutboxRepository outbox,
            CrmConnectionRepository connections) {
        this.settings = settings;
        this.outbox = outbox;
        this.connections = connections;
        // A sleeping Render service can take about a minute to answer its first request.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Scheduled(initialDelayString = "${app.crm.dispatch-initial-delay-ms:20000}",
            fixedDelayString = "${app.crm.dispatch-interval-ms:15000}")
    public void dispatchDue() {
        if (!settings.isReady()) {
            return;
        }
        List<IntegrationOutboxEvent> due = outbox.findDue(LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
        // After one failure for a workspace, hold its remaining events so they don't overtake it.
        Set<UUID> heldBack = new HashSet<>();
        for (IntegrationOutboxEvent event : due) {
            if (heldBack.contains(event.getOrganizationId())) {
                continue;
            }
            if (!deliver(event)) {
                heldBack.add(event.getOrganizationId());
            }
        }
    }

    /** @return true when the event is finished with (delivered or dropped), false when it will be retried. */
    boolean deliver(IntegrationOutboxEvent event) {
        boolean linked = connections.findByOrganizationId(event.getOrganizationId())
                .map(CrmConnection::isActive)
                .orElse(false);
        if (!linked) {
            outbox.delete(event);
            return true;
        }
        try {
            long timestamp = Instant.now().getEpochSecond();
            String signature = RequestSigner.sign(settings.secret(), timestamp, "POST", EVENTS_PATH, event.getPayload());
            restClient.post()
                    .uri(URI.create(settings.baseUrl() + EVENTS_PATH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(RequestSigner.TIMESTAMP_HEADER, Long.toString(timestamp))
                    .header(RequestSigner.SIGNATURE_HEADER, signature)
                    .body(event.getPayload())
                    .retrieve()
                    .toBodilessEntity();
            event.setDeliveredAt(LocalDateTime.now());
            event.setLastError(null);
            outbox.save(event);
            return true;
        } catch (RestClientResponseException ex) {
            recordFailure(event, "HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString());
        } catch (RuntimeException ex) {
            recordFailure(event, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        return false;
    }

    private void recordFailure(IntegrationOutboxEvent event, String error) {
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLastError(error.length() > 1000 ? error.substring(0, 1000) : error);
        event.setNextAttemptAt(LocalDateTime.now().plus(nextDelay(attempts)));
        outbox.save(event);
        if (attempts >= MAX_ATTEMPTS) {
            LOG.error("CRM delivery for event {} (org {}) has failed {} times and will no longer be retried: {}",
                    event.getId(), event.getOrganizationId(), attempts, event.getLastError());
        } else {
            LOG.warn("CRM delivery for event {} failed (attempt {}), retrying at {}: {}",
                    event.getId(), attempts, event.getNextAttemptAt(), event.getLastError());
        }
    }

    /** Delay before the next try; after {@link #MAX_ATTEMPTS} the event is parked so the CRM's reconcile can take over. */
    static Duration nextDelay(int attempts) {
        if (attempts >= MAX_ATTEMPTS) {
            return Duration.ofDays(36_500);
        }
        int index = Math.min(attempts - 1, BACKOFF_MINUTES.length - 1);
        return Duration.ofMinutes(BACKOFF_MINUTES[Math.max(index, 0)]);
    }
}
