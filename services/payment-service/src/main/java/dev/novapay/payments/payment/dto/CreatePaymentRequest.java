package dev.novapay.payments.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for POST /api/v1/payments.
 */
public record CreatePaymentRequest(

        @NotNull(message = "sourceAccountId is required")
        UUID sourceAccountId,

        @NotNull(message = "destinationAccountId is required")
        UUID destinationAccountId,

        @Positive(message = "amountCents must be greater than zero")
        long amountCents,

        @NotNull(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be 3 uppercase letters (ISO 4217)")
        String currency,

        @Size(max = 256, message = "description must be 256 characters or fewer")
        String description
) {}