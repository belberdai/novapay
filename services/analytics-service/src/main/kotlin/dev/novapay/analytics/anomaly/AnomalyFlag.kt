package dev.novapay.analytics.anomaly

import java.time.Instant
import java.util.UUID

/**
 * A flagged anomaly. One row per detection.
 *
 * Each rule writes a flag when its condition is triggered. The
 * triggerPaymentId connects the flag back to the specific event
 * that caused detection.
 */
data class AnomalyFlag(
    val accountId: UUID,
    val flaggedAt: Instant,
    val anomalyType: String,
    val triggerPaymentId: UUID,
    val details: String,
)