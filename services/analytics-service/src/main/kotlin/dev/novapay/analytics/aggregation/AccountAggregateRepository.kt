package dev.novapay.analytics.aggregation

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.model.AttributeValue
import aws.sdk.kotlin.services.dynamodb.model.UpdateItemRequest
import dev.novapay.analytics.event.PaymentCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Maintains per-account rollup metrics in DynamoDB.
 *
 * Each event updates two rows: the source account (debit side) and the
 * destination account (credit side). Updates use atomic ADD expressions —
 * DynamoDB increments the counters server-side, so concurrent writers
 * don't cause lost updates.
 *
 * The consumer relies on PaymentEventLedger's conditional write for
 * deduplication. If the ledger already has the event, the consumer skips
 * the aggregate updates entirely. There's a small window where the
 * ledger could write but an aggregate update could fail — this is
 * documented as a known limitation, with TransactWriteItems being the
 * production hardening path.
 */
@Component
class AccountAggregateRepository(
    private val dynamoDbClient: DynamoDbClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TABLE_NAME = "account_aggregates"
    }

    suspend fun applyEvent(event: PaymentCreatedEvent) {
        // Source account: outbound transaction
        addToAccount(
            accountId = event.sourceAccountId.toString(),
            txCount = "transactionsSent",
            txAmount = "totalCentsSent",
            amountCents = event.amountCents,
            occurredAt = event.occurredAt.toString(),
        )

        // Destination account: inbound transaction
        addToAccount(
            accountId = event.destinationAccountId.toString(),
            txCount = "transactionsReceived",
            txAmount = "totalCentsReceived",
            amountCents = event.amountCents,
            occurredAt = event.occurredAt.toString(),
        )
    }

    private suspend fun addToAccount(
        accountId: String,
        txCount: String,
        txAmount: String,
        amountCents: Long,
        occurredAt: String,
    ) {
        dynamoDbClient.updateItem(UpdateItemRequest {
            this.tableName = TABLE_NAME
            this.key = mapOf("accountId" to AttributeValue.S(accountId))
            this.updateExpression =
                "ADD #count :one, #amount :amount SET #lastActivity = :occurred"
            this.expressionAttributeNames = mapOf(
                "#count" to txCount,
                "#amount" to txAmount,
                "#lastActivity" to "lastActivityAt",
            )
            this.expressionAttributeValues = mapOf(
                ":one" to AttributeValue.N("1"),
                ":amount" to AttributeValue.N(amountCents.toString()),
                ":occurred" to AttributeValue.S(occurredAt),
            )
        })

        log.debug("Updated aggregate for account={}, +{}cents", accountId, amountCents)
    }
}