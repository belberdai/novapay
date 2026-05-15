package dev.novapay.payments.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.novapay.payments.idempotency.IdempotencyRecord;
import dev.novapay.payments.idempotency.IdempotencyRecordRepository;
import dev.novapay.payments.idempotency.IdempotencyService;
import dev.novapay.payments.outbox.OutboxEvent;
import dev.novapay.payments.outbox.OutboxEventRepository;
import dev.novapay.payments.payment.events.PaymentCreatedEvent;
import dev.novapay.payments.payment.events.PaymentStateChangedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the complete payment lifecycle: creation, validation, processing,
 * completion, and failure handling. All operations are idempotent and atomically
 * persisted with their associated events.
 */
@Service
public class PaymentService {

    private static final String AGGREGATE_TYPE = "payment";
    private static final String EVENT_PAYMENT_STATE_CHANGED = "PaymentStateChanged";
    private static final String EVENT_PAYMENT_CREATED = "PaymentCreated";

    private final PaymentRepository paymentRepository;
    private final PaymentStateTransitionRepository transitionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper jsonMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentStateTransitionRepository transitionRepository,
                          IdempotencyRecordRepository idempotencyRecordRepository,
                          OutboxEventRepository outboxEventRepository,
                          Clock clock, IdempotencyService idempotencyService, ObjectMapper jsonMapper) {
        this.paymentRepository = paymentRepository;
        this.transitionRepository = transitionRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.clock = clock;
        this.idempotencyService = idempotencyService;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Create a new payment with full idempotency, audit-log, and outbox semantics.
     * <p>
     * Idempotency contract:
     *   - First request with key K, fingerprint F → creates payment, returns response, stores record.
     *   - Second request with key K, SAME fingerprint F → returns the stored response (no DB writes).
     *   - Second request with key K, DIFFERENT fingerprint → throws IdempotencyMismatchException (422).
     * <p>
     * Concurrency note: two simultaneous requests with the same key are resolved by the
     * UNIQUE PK constraint on idempotency_record. One INSERT succeeds, the other fails
     * with DataIntegrityViolationException — we catch it and re-resolve via the stored record.
     */
    @Transactional
    public Payment createPayment(UUID sourceAccountId,
                                 UUID destinationAccountId,
                                 long amountCents,
                                 String currency,
                                 String description,
                                 String idempotencyKey,
                                 String requestBody) {

        Instant now = Instant.now(clock);
        String fingerprint = idempotencyService.sha256(requestBody);

        // ---- 1. Idempotency check (fast path: already exists) ----
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            return idempotencyService.resolveExisting(existing.get(), fingerprint, idempotencyKey);
        }

        // ---- 2. Create the payment ----
        Payment payment = Payment.create(
                sourceAccountId,
                destinationAccountId,
                amountCents,
                currency,
                idempotencyKey,
                description,
                now
        );
        payment = paymentRepository.save(payment);

        // ---- 3. Record the initial state transition (null → PENDING) ----
        transitionRepository.save(PaymentStateTransition.recordTransition(
                payment.getId(),
                null,
                PaymentStatus.PENDING,
                null,
                now
        ));

        // ---- 4. Queue the outbox event ----
        PaymentCreatedEvent event = new PaymentCreatedEvent(
                payment.getId(),
                payment.getSourceAccountId(),
                payment.getDestinationAccountId(),
                payment.getAmountCents(),
                payment.getCurrency(),
                payment.getStatus(),
                now
        );
        outboxEventRepository.save(OutboxEvent.create(
                AGGREGATE_TYPE,
                payment.getId(),
                EVENT_PAYMENT_CREATED,
                toJson(event)
        ));

        // ---- 5. Store the idempotency record ----
        // We rely on the PK uniqueness for race-condition handling. If two requests
        // arrived simultaneously and both got past step 1, exactly one of them
        // succeeds here. The other gets DataIntegrityViolationException, which the
        // controller layer translates back to the duplicate-request semantics.
        idempotencyRecordRepository.save(IdempotencyRecord.create(
                idempotencyKey,
                payment.getId(),
                fingerprint,
                toJson(payment),
                201
        ));

        return payment;
    }

    @Transactional
    public Payment validate(UUID paymentId) {
        Payment payment = mustFind(paymentId);;
        Instant now = Instant.now(clock);
        PaymentStatus from = payment.getStatus();

        payment.validate(now);

        recordTransitionAndOutbox(payment, from, PaymentStatus.VALIDATED, null, now);
        return payment;
    }

    @Transactional
    public Payment markProcessing(UUID paymentId) {
        Payment payment = mustFind(paymentId);;
        Instant now = Instant.now(clock);
        PaymentStatus from = payment.getStatus();

        payment.markProcessing(now);

        recordTransitionAndOutbox(payment, from, PaymentStatus.PROCESSING, null, now);
        return payment;
    }

    @Transactional
    public Payment complete(UUID paymentId) {
        Payment payment = mustFind(paymentId);
        Instant now = Instant.now(clock);
        PaymentStatus from = payment.getStatus();

        payment.complete(now);

        recordTransitionAndOutbox(payment, from, PaymentStatus.COMPLETED, null, now);
        return payment;
    }

    @Transactional
    public Payment fail(UUID paymentId, String reason) {
        Payment payment = mustFind(paymentId);
        Instant now = Instant.now(clock);
        PaymentStatus from = payment.getStatus();

        payment.fail(reason, now);

        recordTransitionAndOutbox(payment, from, PaymentStatus.FAILED, reason, now);
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getPayment(UUID paymentId) {
        return mustFind(paymentId);
    }

    @Transactional(readOnly = true)
    public Page<Payment> listPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable);
    }

    private Payment mustFind(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private void recordTransitionAndOutbox(Payment payment,
                                          PaymentStatus from,
                                          PaymentStatus to,
                                          String reason,
                                          Instant now) {
        transitionRepository.save(PaymentStateTransition.recordTransition(
                payment.getId(), from, to, reason, now
        ));

        PaymentStateChangedEvent event = new PaymentStateChangedEvent(
                payment.getId(), from, to, reason, now
        );
        outboxEventRepository.save(OutboxEvent.create(
                AGGREGATE_TYPE,
                payment.getId(),
                EVENT_PAYMENT_STATE_CHANGED,
                toJson(event)
        ));
    }

    private String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}