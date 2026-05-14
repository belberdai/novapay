package dev.novapay.payments.payment;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final UUID srcId = UUID.randomUUID();
    private static final UUID destId = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    @Test
    void validate_movesPendingToValidated() {
        // Arrange
        Payment p = Payment.create(srcId, destId, 1000L, "CAD", "key", null, NOW);

        // Act
        p.validate(NOW);

        // Assert
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
    }

    @Test
    void validate_throwsWhenAlreadyCompleted() {
        // Arrange
        Payment p = paymentInState(PaymentStatus.COMPLETED);

        // Act & Assert
        assertThatThrownBy(() -> p.validate(NOW))
                .isInstanceOf(IllegalPaymentStateTransition.class);
    }

    @Test
    void markProcessing_movesValidatedToProcessing() {
        // Arrange
        Payment p = paymentInState(PaymentStatus.VALIDATED);

        // Act
        p.markProcessing(NOW);

        // Assert
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void markProcessing_throwsWhenNotValidated() {
        // Arrange
        Payment p = paymentInState(PaymentStatus.PENDING);

        // Act & Assert
        assertThatThrownBy(() -> p.markProcessing(NOW))
                .isInstanceOf(IllegalPaymentStateTransition.class);
    }

    @Test
    void complete_movesProcessingToCompleted() {
        // Arrange
        Payment p = paymentInState(PaymentStatus.PROCESSING);

        // Act
        p.complete(NOW);

        // Assert
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void complete_throwsWhenNotProcessing() {
        // Arrange
        Payment p = paymentInState(PaymentStatus.PENDING);

        // Act & Assert
        assertThatThrownBy(() -> p.complete(NOW))
                .isInstanceOf(IllegalPaymentStateTransition.class);
    }

    @Test
    void fail_movesFromAnyStateExceptCompletedOrFailed() {
        // Arrange
        Payment p = paymentInState(PaymentStatus.PROCESSING);

        // Act
        p.fail("Insufficient funds", NOW);

        // Assert
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void fail_throwsWhenAlreadyCompleted() {
        // Arrange
        Payment p = paymentInState(PaymentStatus.COMPLETED);

        // Act & Assert
        assertThatThrownBy(() -> p.fail("reason", NOW))
                .isInstanceOf(IllegalPaymentStateTransition.class);
    }

    @Test
    void fail_throwsWhenAlreadyFailed() {
        // Arrange
        Payment p = paymentInState(PaymentStatus.FAILED);

        // Act & Assert
        assertThatThrownBy(() -> p.fail("reason", NOW))
                .isInstanceOf(IllegalPaymentStateTransition.class);
    }

    @Test
    void create_initializes() {
        // Arrange & Act
        Payment p = Payment.create(srcId, destId, 5000L, "USD", "idem-key", "desc", NOW);

        // Assert
        assertThat(p)
                .extracting(
                        Payment::getSourceAccountId,
                        Payment::getDestinationAccountId,
                        Payment::getAmountCents,
                        Payment::getCurrency,
                        Payment::getStatus,
                        Payment::getIdempotencyKey,
                        Payment::getDescription
                )
                .containsExactly(
                        srcId, destId, 5000L, "USD", PaymentStatus.PENDING, "idem-key", "desc"
                );
    }

    private Payment paymentInState(PaymentStatus status) {
        Payment p = Payment.create(srcId, destId, 1000L, "CAD", "key", null, NOW);

        switch (status) {
            case PENDING:
                return p;
            case VALIDATED:
                p.validate(NOW);
                return p;
            case PROCESSING:
                p.validate(NOW);
                p.markProcessing(NOW);
                return p;
            case COMPLETED:
                p.validate(NOW);
                p.markProcessing(NOW);
                p.complete(NOW);
                return p;
            case FAILED:
                p.validate(NOW);
                p.fail("test failure", NOW);
                return p;
            default:
                throw new IllegalArgumentException("Unknown status: " + status);
        }
    }
}