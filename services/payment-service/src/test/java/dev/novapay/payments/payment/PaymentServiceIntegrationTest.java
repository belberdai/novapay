package dev.novapay.payments.payment;

import dev.novapay.payments.TestcontainersConfiguration;
import dev.novapay.payments.account.Account;
import dev.novapay.payments.account.AccountRepository;
import dev.novapay.payments.idempotency.IdempotencyMismatchException;
import dev.novapay.payments.idempotency.IdempotencyRecordRepository;
import dev.novapay.payments.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentServiceIntegrationTest {

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
        assertThat(paymentRepository.findById(p.getId())).isPresent();
        assertThat(transitionRepository.findByPaymentIdOrderByOccurredAtAsc(p.getId())).hasSize(1);
        assertThat(outboxEventRepository.findAll()).hasSize(1);
        assertThat(idempotencyRecordRepository.findById(idempotencyKey)).isPresent();
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
}