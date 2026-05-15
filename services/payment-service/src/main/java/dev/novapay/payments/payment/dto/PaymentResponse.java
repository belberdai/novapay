package dev.novapay.payments.payment.dto;

import dev.novapay.payments.payment.Payment;
import dev.novapay.payments.payment.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for payment endpoints.
 */
public record PaymentResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        long amountCents,
        String currency,
        PaymentStatus status,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getSourceAccountId(),
                payment.getDestinationAccountId(),
                payment.getAmountCents(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getDescription(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}