package dev.novapay.payments.payment.events;

import dev.novapay.payments.payment.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a new payment is created (the PENDING state).
 * Downstream services (analytics, notification, fraud detection) consume this
 * to track account activity.
 */
public record PaymentCreatedEvent(
        UUID paymentId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        long amountCents,
        String currency,
        PaymentStatus status,
        Instant occurredAt
) {}