package dev.novapay.analytics.aggregation

import java.time.Instant
import java.util.UUID

/**
 * Per-account rollup metrics. One row per account in DynamoDB.
 *
 * Note: this is a read model. We never construct one of these in code to
 * write to DynamoDB — we use AccountAggregateRepository's atomic ADD
 * expressions instead. This class is only used when reading from
 * DynamoDB (e.g., for the HTTP query endpoint tomorrow).
 */
data class AccountAggregate(
    val accountId: UUID,
    val transactionsSent: Long,
    val transactionsReceived: Long,
    val totalCentsSent: Long,
    val totalCentsReceived: Long,
    val lastActivityAt: Instant?,
)