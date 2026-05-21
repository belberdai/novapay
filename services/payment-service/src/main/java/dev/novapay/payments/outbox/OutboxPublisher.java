package dev.novapay.payments.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Background poller that publishes unpublished outbox events to SNS.
 * <p>
 * Runs on a fixed interval. Each cycle:
 *   1. Fetch a bounded batch of unpublished events (with row-level lock to
 *      prevent multiple poller instances from picking up the same rows).
 *   2. For each event: publish to SNS, then mark published_at = now().
 *   3. Commit the transaction. If any step fails, the transaction rolls back
 *      and the events stay unpublished — they'll be retried next cycle.
 * <p>
 * At-least-once delivery: if the process crashes between SNS publish and the
 * DB update, the event will be published again next cycle. Downstream
 * consumers (analytics service) must be idempotent.
 * <a href="https://blog.bytebytego.com/p/at-most-once-at-least-once-exactly">...</a>
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final SnsEventPublisher snsPublisher;
    private final Clock clock;
    private final int batchSize;

    public OutboxPublisher(OutboxEventRepository outboxRepository,
                           SnsEventPublisher snsPublisher,
                           Clock clock,
                           @Value("${outbox.poller.batch-size:100}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.snsPublisher = snsPublisher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /**
     * Poll for unpublished events and publish them. Transactional so the
     * SELECT FOR UPDATE lock is held until commit, and so the published_at
     * updates roll back on SNS failure.
     */
    private static final int MAX_PUBLISH_ATTEMPTS = 5;

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findPendingForUpdate(
                PageRequest.of(0, batchSize));

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Publishing {} outbox events", pending.size());
        Instant now = Instant.now(clock);
        int published = 0;
        int poisoned = 0;

        for (OutboxEvent event : pending) {
            try {
                snsPublisher.publish(event);
                event.markPublished(now);
                published++;
            } catch (Exception e) {
                event.recordPublishAttempt();
                if (event.getPublishAttempts() >= MAX_PUBLISH_ATTEMPTS) {
                    event.markPoisoned(now);
                    poisoned++;
                    log.error("Outbox event {} poisoned after {} attempts. Last error: {}",
                            event.getId(),
                            event.getPublishAttempts(),
                            rootCauseMessage(e));
                    log.debug("Stack trace for poisoned event {}:", event.getId(), e);
                } else {
                    log.warn("Outbox event {} publish failed (attempt {}/{}). Cause: {}",
                            event.getId(),
                            event.getPublishAttempts(),
                            MAX_PUBLISH_ATTEMPTS,
                            rootCauseMessage(e));
                }
            }
        }

        if (published > 0 || poisoned > 0) {
            log.info("Outbox cycle: published={}, poisoned={}", published, poisoned);
        }
    }

    /**
     * Walks the exception cause chain to the deepest message. SDK exceptions
     * often wrap the real failure (HttpHostConnectException → ConnectException →
     * "Connection refused") several layers down. The root message is the
     * operationally useful part.
     */
    private String rootCauseMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }
}