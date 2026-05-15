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
    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findUnpublishedForUpdate(
                PageRequest.of(0, batchSize));

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Publishing {} outbox events", pending.size());
        Instant now = Instant.now(clock);

        for (OutboxEvent event : pending) {
            try {
                snsPublisher.publish(event);
                event.markPublished(now);
            } catch (Exception e) {
                // If publish fails for ANY event, abort the batch.
                // The transaction rolls back; nothing is marked published.
                // Next cycle will retry the whole batch from the start.
                log.error("Failed to publish outbox event {} — aborting batch", event.getId(), e);
                throw e;
            }
        }

        log.info("Published {} outbox events", pending.size());
    }
}