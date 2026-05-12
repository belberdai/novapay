-- ============================================================================
-- V1__initial_schema.sql
-- ============================================================================
-- Initial schema for the NovaPay payment service.
--
-- Design principles encoded here:
--   1. UUIDs as primary keys for all customer-facing entities (payments,
--      accounts).
--   2. Money stored as integer cents + currency code. No floating-point ambiguity.
--   3. State changes are append-only in payment_state_transition.
--   4. Idempotency enforced at the database level (UNIQUE PK).
--   5. Transactional outbox pattern for SNS publishing. Events are written
--      to outbox_event in the same transaction as the payment update, then
--      relayed asynchronously by a poller (Dual Write Problem).
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- ACCOUNT
-- ============================================================================
-- Represents a participant in a payment (source or destination).
-- ============================================================================

CREATE TABLE account (
                         id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                         account_number  VARCHAR(32)  NOT NULL UNIQUE,
                         currency        CHAR(3)      NOT NULL,
                         status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
                         created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Enforce valid statuses at the DB level. The Java enum and the DB
    -- constraint must agree. If a bug in the app tries to insert 'PENDING_REVIEW',
    -- Postgres rejects it.
                         CONSTRAINT account_status_valid
                             CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    -- Currency codes are exactly 3 uppercase letters.
                         CONSTRAINT account_currency_valid
                             CHECK (currency ~ '^[A-Z]{3}$')
    );

-- ============================================================================
-- PAYMENT
-- ============================================================================
-- Represents a request to move money from one account to another, with its current state.
-- ============================================================================

CREATE TABLE payment (
                         id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                         source_account_id       UUID         NOT NULL REFERENCES account(id),
                         destination_account_id  UUID         NOT NULL REFERENCES account(id),
                         amount_cents            BIGINT       NOT NULL,
                         currency                CHAR(3)      NOT NULL,
                         status                  VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    -- Client-provided idempotency key. The full record (with response body,
    -- fingerprint, etc.) lives in idempotency_record; this column is a
    -- denormalized copy for fast direct lookup.
                         idempotency_key         VARCHAR(128) NOT NULL,
                         description             VARCHAR(256),
                         created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
                         updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- An account cannot send money to itself.
                         CONSTRAINT payment_source_dest_different
                             CHECK (source_account_id <> destination_account_id),
    -- Amounts must be strictly positive. Reversals are separate payment rows,
    -- not negative amounts on existing rows.
                         CONSTRAINT payment_amount_positive
                             CHECK (amount_cents > 0),
    -- Currency codes are exactly 3 uppercase letters.
                         CONSTRAINT payment_currency_valid
                             CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT payment_status_valid
        CHECK (status IN ('PENDING', 'VALIDATED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX payment_source_account_idx
    ON payment (source_account_id, created_at DESC);

CREATE INDEX payment_dest_account_idx
    ON payment (destination_account_id, created_at DESC);

CREATE INDEX payment_processing_idx
    ON payment (created_at)
    WHERE status IN ('PENDING', 'VALIDATED', 'PROCESSING');

-- ============================================================================
-- PAYMENT_STATE_TRANSITION
-- ============================================================================
-- Append-only audit log. One row per state change of a payment.
-- A payment that goes PENDING -> VALIDATED -> PROCESSING -> COMPLETED will
-- have FOUR rows here (the initial PENDING with from_state = NULL, plus three
-- transitions).
-- ============================================================================

CREATE TABLE payment_state_transition (
                                          id            BIGSERIAL    PRIMARY KEY,
                                          payment_id    UUID         NOT NULL REFERENCES payment(id) ON DELETE CASCADE,
                                          from_state    VARCHAR(16),
                                          to_state      VARCHAR(16)  NOT NULL,
                                          reason        VARCHAR(256),
                                          actor         VARCHAR(64)  NOT NULL DEFAULT 'system',
                                          occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                          CONSTRAINT pst_state_valid
                                              CHECK (
                                                  to_state IN ('PENDING', 'VALIDATED', 'PROCESSING', 'COMPLETED', 'FAILED')
                                                      AND (from_state IS NULL OR from_state IN ('PENDING', 'VALIDATED', 'PROCESSING', 'COMPLETED', 'FAILED'))
                                                  )
);
CREATE INDEX pst_payment_idx
    ON payment_state_transition (payment_id, occurred_at);

-- ============================================================================
-- IDEMPOTENCY_RECORD
-- ============================================================================
-- Stores the original response for each idempotency key, so duplicate requests
-- return byte-identical responses. The primary key IS the idempotency key itself.
-- ============================================================================

CREATE TABLE idempotency_record (
                                    idempotency_key       VARCHAR(128) PRIMARY KEY,
                                    payment_id            UUID         NOT NULL REFERENCES payment(id),
                                    request_fingerprint   VARCHAR(64)  NOT NULL,
                                    response_body         JSONB        NOT NULL,
                                    response_status       SMALLINT     NOT NULL,
                                    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                    expires_at            TIMESTAMPTZ
);

-- ============================================================================
-- OUTBOX_EVENT
-- ============================================================================
-- Transactional outbox pattern. Solves the dual-write problem:
-- you cannot atomically update Postgres AND publish to SNS, because they're
-- two systems. So we don't try.
--
-- Instead: in the same transaction that updates the payment, we INSERT a row
-- here. A separate background poller reads new outbox rows, publishes to
-- SNS, and marks them sent. If SNS is down, the poller retries.
-- If the poller crashes, restart and pick up where it left off.
--
-- The partial index on (id) WHERE published_at IS NULL keeps the poller's
-- query fast even when the table has millions of rows — only unpublished
-- rows are indexed.
-- ============================================================================

CREATE TABLE outbox_event (
                              id              BIGSERIAL    PRIMARY KEY,
    -- Aggregate identifies what kind of entity this event is about.
    -- For now always 'payment', but the column makes the table reusable.
                              aggregate_type  VARCHAR(32)  NOT NULL,
                              aggregate_id    UUID         NOT NULL,
    -- The event name. Examples: 'PaymentStateChanged', 'PaymentCreated'.
                              event_type      VARCHAR(64)  NOT NULL,
    -- The full event body to send to SNS, as JSON.
                              payload         JSONB        NOT NULL,
                              created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- NULL = not yet published. Set to now() once SNS confirms receipt.
                              published_at    TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx
    ON outbox_event (id)
    WHERE published_at IS NULL;

CREATE INDEX outbox_aggregate_idx
    ON outbox_event (aggregate_type, aggregate_id, created_at);
