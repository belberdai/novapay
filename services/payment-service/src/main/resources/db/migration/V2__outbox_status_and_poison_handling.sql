-- Refactor outbox state model to use an explicit status column.
--
-- Previously, state was encoded by which timestamp column was set
-- (published_at NULL = pending, published_at NOT NULL = published).
-- This refactor introduces a status column with CHECK-enforced values,
-- making invariants explicit at the DB layer.
--
-- Adds poison-message handling: publish_attempts counter and a POISONED
-- status for events that fail to publish after MAX_PUBLISH_ATTEMPTS tries.
-- Poisoned events are skipped by the poller; operators can restore them
-- by setting status back to PENDING.

ALTER TABLE outbox_event
    ADD COLUMN status            VARCHAR(20),
    ADD COLUMN publish_attempts  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN poisoned_at       TIMESTAMPTZ;

-- Backfill status from existing data.
UPDATE outbox_event
SET status = CASE
                 WHEN published_at IS NOT NULL THEN 'PUBLISHED'
                 ELSE 'PENDING'
    END;

-- Now that status is populated, make it NOT NULL with a CHECK constraint.
ALTER TABLE outbox_event
    ALTER COLUMN status SET NOT NULL,
    ADD CONSTRAINT outbox_event_status_valid
        CHECK (status IN ('PENDING', 'PUBLISHED', 'POISONED'));

-- Drop the old partial index and replace with a status-based one.
DROP INDEX IF EXISTS outbox_unpublished_idx;

CREATE INDEX outbox_pending_idx
    ON outbox_event (id)
    WHERE status = 'PENDING';