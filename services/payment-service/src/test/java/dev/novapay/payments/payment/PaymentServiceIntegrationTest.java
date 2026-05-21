package dev.novapay.payments.payment;

import dev.novapay.payments.TestcontainersConfiguration;
import dev.novapay.payments.account.Account;
import dev.novapay.payments.account.AccountRepository;
import dev.novapay.payments.idempotency.IdempotencyMismatchException;
import dev.novapay.payments.idempotency.IdempotencyRecordRepository;
import dev.novapay.payments.outbox.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentServiceIntegrationTest {

    @MockitoBean
    private SnsEventPublisher snsEventPublisher;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentStateTransitionRepository transitionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private AccountRepository accountRepository;

    private UUID srcId;
    private UUID destId;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        transitionRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        paymentRepository.deleteAll();
        accountRepository.deleteAll();
        Account source = accountRepository.save(Account.create("ACC-SRC-001", "CAD"));
        Account dest = accountRepository.save(Account.create("ACC-DST-001", "CAD"));
        srcId = source.getId();
        destId = dest.getId();
    }

    @Test
    void createPayment_persistsPaymentTransitionAndOutbox() {
        // Arrange
        String idempotencyKey = "idem-key-1";

        // Act
        Payment p = paymentService.createPayment(srcId, destId, 1000L, "CAD",
                "desc", idempotencyKey, "{\"amount\":1000}");

        // Assert
        UUID paymentId = p.getId();
        assertThat(paymentRepository.findById(paymentId)).isPresent();
        assertThat(transitionRepository.findByPaymentIdOrderByOccurredAtAsc(paymentId)).hasSize(1);
        assertThat(idempotencyRecordRepository.findById(idempotencyKey)).isPresent();
        OutboxEvent event = outboxEventRepository.findAll().getFirst();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getPublishAttempts()).isZero();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getPoisonedAt()).isNull();
        assertThat(event.getEventType()).isEqualTo("PaymentCreated");
        assertThat(event.getAggregateId()).isEqualTo(paymentId);
    }

    @Test
    void createPayment_returnsOriginalResponseOnDuplicateKey() {
        // Arrange
        String idempotencyKey = "idem-key-2";
        Payment first = paymentService.createPayment(srcId, destId, 1000L, "CAD",
                "desc", idempotencyKey, "{\"amount\":1000}");

        // Act
        Payment second = paymentService.createPayment(srcId, destId, 1000L, "CAD",
                "desc", idempotencyKey, "{\"amount\":1000}");

        // Assert
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void createPayment_throwsOnFingerprintMismatch() {
        // Arrange
        String idempotencyKey = "idem-key-3";
        paymentService.createPayment(srcId, destId, 1000L, "CAD", "desc",
                idempotencyKey, "{\"amount\":1000}");

        // Act & Assert
        assertThatThrownBy(() -> paymentService.createPayment(srcId, destId, 2000L, "CAD",
                "desc", idempotencyKey, "{\"amount\":2000}"))
                .isInstanceOf(IdempotencyMismatchException.class);
    }

    @Test
    void validate_transitionsPaymentAndRecordsEvent() {
        // Arrange
        Payment payment = paymentService.createPayment(srcId, destId, 1000L, "CAD",
                "desc", "idem-key-4", "{\"amount\":1000}");

        // Act
        Payment validated = paymentService.validate(payment.getId());

        // Assert
        assertThat(validated.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(transitionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId())).hasSize(2);
        assertThat(outboxEventRepository.findAll()).hasSize(2);
    }

    @Test
    void markProcessing_transitionsPaymentAndRecordsEvent() {
        // Arrange
        Payment payment = paymentService.createPayment(srcId, destId, 1000L, "CAD",
                "desc", "idem-key-5", "{\"amount\":1000}");
        paymentService.validate(payment.getId());

        // Act
        Payment processing = paymentService.markProcessing(payment.getId());

        // Assert
        assertThat(processing.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(transitionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId())).hasSize(3);
        assertThat(outboxEventRepository.findAll()).hasSize(3);
    }

    @Test
    void complete_transitionsPaymentAndRecordsEvent() {
        // Arrange
        Payment payment = paymentService.createPayment(srcId, destId, 1000L, "CAD",
                "desc", "idem-key-6", "{\"amount\":1000}");
        paymentService.validate(payment.getId());
        paymentService.markProcessing(payment.getId());

        // Act
        Payment completed = paymentService.complete(payment.getId());

        // Assert
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(transitionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId())).hasSize(4);
    }

    @Test
    void fail_transitionsPaymentAndRecordsEvent() {
        // Arrange
        Payment payment = paymentService.createPayment(srcId, destId, 1000L, "CAD",
                "desc", "idem-key-7", "{\"amount\":1000}");
        String failureReason = "Insufficient funds";

        // Act
        Payment failed = paymentService.fail(payment.getId(), failureReason);

        // Assert
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(transitionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId())).hasSize(2);
    }

    @Test
    void getPayment_returnsPersistedPayment() {
        // Arrange
        Payment created = paymentService.createPayment(srcId, destId, 5000L, "USD",
                "test desc", "idem-key-8", "{\"amount\":5000}");

        // Act
        Payment retrieved = paymentService.getPayment(created.getId());

        // Assert
        assertThat(retrieved.getId()).isEqualTo(created.getId());
        assertThat(retrieved.getAmountCents()).isEqualTo(5000L);
        assertThat(retrieved.getCurrency()).isEqualTo("USD");
    }

    @Test
    void getPayment_throwsWhenPaymentNotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act & Assert
        assertThatThrownBy(() -> paymentService.getPayment(nonExistentId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void outboxPollerPublishesPendingEvents() {
        // Arrange
        paymentService.createPayment(
                srcId,
                destId,
                1500L,
                "CAD",
                "test description",
                "test-key-publisher-1",
                "{}"
        );
        OutboxEvent before = outboxEventRepository.findAll().getFirst();
        assertThat(before.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(before.getPublishedAt()).isNull();

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        OutboxEvent after = outboxEventRepository.findById(before.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(after.getPublishedAt()).isNotNull();
        assertThat(after.getPublishAttempts()).isZero();   // no retries needed
        verify(snsEventPublisher).publish(any(OutboxEvent.class));
    }

    @Test
    void outboxPollerPoisonsEventAfterMaxAttempts() {
        // Arrange
        doThrow(new RuntimeException("simulated SNS outage"))
                .when(snsEventPublisher).publish(any(OutboxEvent.class));
        paymentService.createPayment(
                srcId,
                destId,
                1500L,
                "CAD",
                "test description",
                "test-key-poison-1",
                "{}"
        );
        OutboxEvent before = outboxEventRepository.findAll().get(0);
        assertThat(before.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        // Act
        for (int i = 0; i < 5; i++) {
            outboxPublisher.publishPendingEvents();
        }

        // Arrange
        OutboxEvent after = outboxEventRepository.findById(before.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OutboxEventStatus.POISONED);
        assertThat(after.getPublishAttempts()).isEqualTo(5);
        assertThat(after.getPoisonedAt()).isNotNull();
        assertThat(after.getPublishedAt()).isNull();   // never published
        verify(snsEventPublisher, times(5)).publish(any(OutboxEvent.class));
    }
}