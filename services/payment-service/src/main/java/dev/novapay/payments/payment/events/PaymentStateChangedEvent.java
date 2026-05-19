package dev.novapay.payments.payment.events;

import dev.novapay.payments.payment.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on every state transition after creation.
 * Reason is non-null only for FAILED transitions.
 */
public record PaymentStateChangedEvent(
        String eventType,
        UUID paymentId,
        PaymentStatus fromStatus,
        PaymentStatus toStatus,
        String reason,
        Instant occurredAt
) {
    public static PaymentStateChangedEvent of(
            UUID paymentId,
            PaymentStatus fromStatus,
            PaymentStatus toStatus,
            String reason,
            Instant occurredAt) {
        return new PaymentStateChangedEvent(
                "PaymentStateChanged",
                paymentId,
                fromStatus,
                toStatus,
                reason,
                occurredAt);
    }
}