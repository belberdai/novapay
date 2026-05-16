package dev.novapay.payments.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateMachineTest {

    private static final Instant CREATED_AT = Instant.parse("2026-05-15T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-05-15T10:05:00Z");
    private static final Instant LATER_STILL = Instant.parse("2026-05-15T10:10:00Z");

    private static final UUID SRC = UUID.randomUUID();
    private static final UUID DEST = UUID.randomUUID();

    private Payment newPendingPayment() {
        return Payment.create(SRC, DEST, 1000L, "CAD", "test-key", "test", CREATED_AT);
    }

    // ===================================================================
    // Creation
    // ===================================================================

    @Nested
    @DisplayName("when created")
    class WhenCreated {

        @Test
        void startsInPendingState() {
            Payment p = newPendingPayment();

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        void hasCreatedAtAndUpdatedAtSet() {
            Payment p = newPendingPayment();

            assertThat(p.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(p.getUpdatedAt()).isEqualTo(CREATED_AT);
        }
    }

    // ===================================================================
    // validate(): PENDING -> VALIDATED
    // ===================================================================

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        void transitionsPendingToValidated() {
            Payment p = newPendingPayment();

            p.validate(LATER);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
        }

        @Test
        void updatesUpdatedAtToTheProvidedInstant() {
            Payment p = newPendingPayment();

            p.validate(LATER);

            assertThat(p.getUpdatedAt()).isEqualTo(LATER);
        }

        @Test
        void doesNotChangeCreatedAt() {
            Payment p = newPendingPayment();

            p.validate(LATER);

            assertThat(p.getCreatedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        void throwsWhenAlreadyValidated() {
            Payment p = newPendingPayment();
            p.validate(LATER);

            assertThatThrownBy(() -> p.validate(LATER_STILL))
                    .isInstanceOf(IllegalPaymentStateTransition.class)
                    .hasMessageContaining("VALIDATED");
        }

        @Test
        void throwsWhenInProcessing() {
            Payment p = newPendingPayment();
            p.validate(LATER);
            p.markProcessing(LATER_STILL);

            assertThatThrownBy(() -> p.validate(LATER_STILL))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }

        @Test
        void throwsWhenCompleted() {
            Payment p = completedPayment();

            assertThatThrownBy(() -> p.validate(LATER))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }

        @Test
        void throwsWhenFailed() {
            Payment p = newPendingPayment();
            p.fail("test failure", LATER);

            assertThatThrownBy(() -> p.validate(LATER_STILL))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }
    }

    // ===================================================================
    // markProcessing(): VALIDATED -> PROCESSING
    // ===================================================================

    @Nested
    @DisplayName("markProcessing()")
    class MarkProcessing {

        @Test
        void transitionsValidatedToProcessing() {
            Payment p = newPendingPayment();
            p.validate(LATER);

            p.markProcessing(LATER_STILL);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
            assertThat(p.getUpdatedAt()).isEqualTo(LATER_STILL);
        }

        @Test
        void throwsWhenStillPending() {
            Payment p = newPendingPayment();

            assertThatThrownBy(() -> p.markProcessing(LATER))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }

        @Test
        void throwsWhenAlreadyProcessing() {
            Payment p = newPendingPayment();
            p.validate(LATER);
            p.markProcessing(LATER_STILL);

            assertThatThrownBy(() -> p.markProcessing(LATER_STILL))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }

        @Test
        void throwsWhenCompleted() {
            Payment p = completedPayment();

            assertThatThrownBy(() -> p.markProcessing(LATER))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }

        @Test
        void throwsWhenFailed() {
            Payment p = newPendingPayment();
            p.fail("test failure", LATER);

            assertThatThrownBy(() -> p.markProcessing(LATER_STILL))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }
    }

    // ===================================================================
    // complete(): PROCESSING -> COMPLETED
    // ===================================================================

    @Nested
    @DisplayName("complete()")
    class Complete {

        @Test
        void transitionsProcessingToCompleted() {
            Payment p = newPendingPayment();
            p.validate(LATER);
            p.markProcessing(LATER_STILL);

            p.complete(LATER_STILL);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        void throwsWhenStillPending() {
            Payment p = newPendingPayment();

            assertThatThrownBy(() -> p.complete(LATER))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }

        @Test
        void throwsWhenValidated() {
            Payment p = newPendingPayment();
            p.validate(LATER);

            assertThatThrownBy(() -> p.complete(LATER_STILL))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }

        @Test
        void throwsWhenAlreadyCompleted() {
            Payment p = completedPayment();

            assertThatThrownBy(() -> p.complete(LATER))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }

        @Test
        void throwsWhenFailed() {
            Payment p = newPendingPayment();
            p.fail("test failure", LATER);

            assertThatThrownBy(() -> p.complete(LATER_STILL))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }
    }

    // ===================================================================
    // fail(): * -> FAILED (from any non-terminal state)
    // ===================================================================

    @Nested
    @DisplayName("fail()")
    class Fail {

        @Test
        void transitionsPendingToFailed() {
            Payment p = newPendingPayment();

            p.fail("insufficient funds", LATER);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(p.getUpdatedAt()).isEqualTo(LATER);
        }

        @Test
        void transitionsValidatedToFailed() {
            Payment p = newPendingPayment();
            p.validate(LATER);

            p.fail("daily limit exceeded", LATER_STILL);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        void transitionsProcessingToFailed() {
            Payment p = newPendingPayment();
            p.validate(LATER);
            p.markProcessing(LATER_STILL);

            p.fail("payment rail timeout", LATER_STILL);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        void throwsWhenAlreadyCompleted() {
            // Terminal state — can never transition out, even to FAILED
            Payment p = completedPayment();

            assertThatThrownBy(() -> p.fail("retroactive failure", LATER))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }

        @Test
        void throwsWhenAlreadyFailed() {
            Payment p = newPendingPayment();
            p.fail("first failure", LATER);

            assertThatThrownBy(() -> p.fail("second failure", LATER_STILL))
                    .isInstanceOf(IllegalPaymentStateTransition.class);
        }
    }

    // ===================================================================
    // Test helper
    // ===================================================================

    private Payment completedPayment() {
        Payment p = newPendingPayment();
        p.validate(LATER);
        p.markProcessing(LATER_STILL);
        p.complete(LATER_STILL);
        return p;
    }
}