package prod.tint_wym.novora_backend.tenancy;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;

/**
 * Hourly sweeper that flips trials whose {@code trial_expires_at} is in the past from
 * {@code TRIAL/ACTIVE} to {@code EXPIRED/READ_ONLY}. Cheap, idempotent, and a defence-in-depth on
 * top of the per-request guard in {@link TrialExpiryFilter} so admin queries on the
 * {@code organizations} table reflect the right state quickly.
 *
 * <p>Runs in every Spring instance — that's fine because the underlying UPDATE has a tight WHERE
 * clause and Postgres serializes the writes.
 */
@Component
public class TrialExpirySweeper {

    private static final Logger LOG = LoggerFactory.getLogger(TrialExpirySweeper.class);

    private final OrganizationRepository organizationRepository;

    public TrialExpirySweeper(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        int flipped = organizationRepository.expireTrialsBefore(now);
        if (flipped > 0) {
            LOG.info("trial sweeper: expired {} organization(s) at {}", flipped, now);
        }
    }
}
