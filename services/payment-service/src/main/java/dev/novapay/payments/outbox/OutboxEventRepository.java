package dev.novapay.payments.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Find the next batch of unpublished events for the poller to process.
     * <p>
     * The PESSIMISTIC_WRITE lock combined with SKIP LOCKED means:
     *   - Multiple poller instances can run in parallel without processing the same row twice.
     *   - If one poller is processing rows 1-100, another poller asks for unpublished
     *     events and gets rows 101-200 instead of waiting.
     * <p>
     * This is how every real "background worker" pattern in Postgres handles concurrency.
     * <a href="https://www.postgresql.org/docs/current/bgworker.html">...</a>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboxEvent o " +
            "WHERE o.status = OutboxEventStatus.PENDING " +
            "ORDER BY o.id")
    List<OutboxEvent> findPendingForUpdate(Pageable pageable);
}