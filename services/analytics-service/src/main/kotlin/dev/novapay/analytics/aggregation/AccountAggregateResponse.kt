package dev.novapay.analytics.aggregation

import java.time.Instant
import java.util.UUID

data class AccountAggregateResponse(
    val accountId: UUID,
    val transactionsSent: Long,
    val transactionsReceived: Long,
    val totalCentsSent: Long,
    val totalCentsReceived: Long,
    val lastActivityAt: Instant?,
) {
    companion object {
        fun from(aggregate: AccountAggregate): AccountAggregateResponse =
            AccountAggregateResponse(
                accountId = aggregate.accountId,
                transactionsSent = aggregate.transactionsSent,
                transactionsReceived = aggregate.transactionsReceived,
                totalCentsSent = aggregate.totalCentsSent,
                totalCentsReceived = aggregate.totalCentsReceived,
                lastActivityAt = aggregate.lastActivityAt,
            )
    }
}