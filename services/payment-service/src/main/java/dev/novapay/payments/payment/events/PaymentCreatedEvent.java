package dev.novapay.payments.payment.events;

import dev.novapay.payments.payment.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a new payment is created (the PENDING state).
 * Downstream services (analytics, notification, fraud detection) consume this
 * to track account activity.
 * <p>
 * With the eventType field, consumers can route by type from the message body,
 * without depending on transport-layer metadata (SNS MessageAttributes)
 */
public record PaymentCreatedEvent(
        String eventType,
        UUID paymentId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        long amountCents,
        String currency,
        PaymentStatus status,
        Instant occurredAt
) {
    public static PaymentCreatedEvent of(
            UUID paymentId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amountCents,
            String currency,
            PaymentStatus status,
            Instant occurredAt) {
        return new PaymentCreatedEvent(
                "PaymentCreated",
                paymentId,
                sourceAccountId,
                destinationAccountId,
                amountCents,
                currency,
                status,
                occurredAt);
    }
}