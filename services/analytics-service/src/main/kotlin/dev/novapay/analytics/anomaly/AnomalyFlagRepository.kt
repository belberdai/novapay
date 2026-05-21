package dev.novapay.analytics.anomaly

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.model.AttributeValue
import aws.sdk.kotlin.services.dynamodb.model.PutItemRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AnomalyFlagRepository(
    private val dynamoDbClient: DynamoDbClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TABLE_NAME = "anomaly_flags"
    }

    suspend fun record(flag: AnomalyFlag) {
        val item = mapOf(
            "accountId" to AttributeValue.S(flag.accountId.toString()),
            "flaggedAt" to AttributeValue.S(flag.flaggedAt.toString()),
            "anomalyType" to AttributeValue.S(flag.anomalyType),
            "triggerPaymentId" to AttributeValue.S(flag.triggerPaymentId.toString()),
            "details" to AttributeValue.S(flag.details),
        )

        dynamoDbClient.putItem(PutItemRequest {
            this.tableName = TABLE_NAME
            this.item = item
        })

        log.info("Recorded anomaly flag: account={}, type={}, payment={}",
            flag.accountId, flag.anomalyType, flag.triggerPaymentId)
    }
}