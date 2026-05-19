package dev.novapay.analytics.ledger

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.model.AttributeValue
import aws.sdk.kotlin.services.dynamodb.model.PutItemRequest
import dev.novapay.analytics.event.PaymentCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Append-only ledger of payment events written to DynamoDB.
 * Each event is a row keyed by (paymentId, occurredAt).
 */
@Component
class PaymentEventLedger(
    private val dynamoDbClient: DynamoDbClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TABLE_NAME = "payment_event_ledger"
    }

    suspend fun record(event: PaymentCreatedEvent) {
        val item = mapOf(
            "paymentId" to AttributeValue.S(event.paymentId.toString()),
            "occurredAt" to AttributeValue.S(event.occurredAt.toString()),
            "eventType" to AttributeValue.S(event.eventType),
            "sourceAccountId" to AttributeValue.S(event.sourceAccountId.toString()),
            "destinationAccountId" to AttributeValue.S(event.destinationAccountId.toString()),
            "amountCents" to AttributeValue.N(event.amountCents.toString()),
            "currency" to AttributeValue.S(event.currency),
            "status" to AttributeValue.S(event.status.toString()),
        )

        dynamoDbClient.putItem(PutItemRequest {
            this.tableName = TABLE_NAME
            this.item = item
        })

        log.debug("Wrote event to ledger: paymentId={}, occurredAt={}", event.paymentId, event.occurredAt)
    }
}