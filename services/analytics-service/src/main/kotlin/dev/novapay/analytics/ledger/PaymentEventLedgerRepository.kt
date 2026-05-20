package dev.novapay.analytics.ledger

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.model.AttributeValue
import aws.sdk.kotlin.services.dynamodb.model.ConditionalCheckFailedException
import aws.sdk.kotlin.services.dynamodb.model.PutItemRequest
import dev.novapay.analytics.event.PaymentCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Append-only ledger of payment events written to DynamoDB.
 * Each event is a row keyed by (paymentId, occurredAt).
 */
@Component
class PaymentEventLedgerRepository(
    private val dynamoDbClient: DynamoDbClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TABLE_NAME = "payment_event_ledger"
    }

    suspend fun record(event: PaymentCreatedEvent): Boolean {
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

        return try {
            dynamoDbClient.putItem(PutItemRequest {
                this.tableName = TABLE_NAME
                this.item = item
                this.conditionExpression = "attribute_not_exists(paymentId)"
            })
            log.debug("Recorded event in ledger: paymentId={}", event.paymentId)
            true // record succeded
        } catch (e: ConditionalCheckFailedException) {
            log.info("Skipping duplicate event: paymentId={}, occurredAt={}",
                event.paymentId, event.occurredAt)
            false // record failed
        }
    }
}