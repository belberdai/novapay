package dev.novapay.analytics.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant
import java.util.*

/**
 * Mirrors PaymentCreatedEvent emitted by payment-service. Analytics-service
 * consumes this to track account activity.
 *
 * The eventType field lets us route by type from the message body alone.
 *
 * "@JsonIgnoreProperties(ignoreUnknown = true)" means: if the upstream service
 * adds fields in the future, our consumer ignores them.
 * Tolerant readers, strict writers (event-driven pattern)
 *
 * https://medium.com/digitalfrontiers/demystified-tolerant-reader-ca07d6bea602
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentCreatedEvent(
    val eventType: String,
    val paymentId: UUID,
    val sourceAccountId: UUID,
    val destinationAccountId: UUID,
    val amountCents: Long,
    val currency: String,
    val status: PaymentStatus, // same enum as in payment-service
    val occurredAt: Instant,
)

enum class PaymentStatus {
    PENDING,
    VALIDATED,
    PROCESSING,
    COMPLETED,
    FAILED
}