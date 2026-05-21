package dev.novapay.analytics.consumer

import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.DeleteMessageRequest
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import dev.novapay.analytics.aggregation.AccountAggregateRepository
import dev.novapay.analytics.anomaly.AnomalyDetector
import dev.novapay.analytics.event.PaymentCreatedEvent
import dev.novapay.analytics.ledger.PaymentEventLedgerRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
// Constructor injection via primary constructor
class SqsMessageConsumer(
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    private val paymentEventLedgerRepository: PaymentEventLedgerRepository,
    private val accountAggregateRepository: AccountAggregateRepository,
    private val anomalyDetector: AnomalyDetector,
    @Value("\${aws.sqs.queue-url}") private val queueUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 2000)
    fun poll() = runBlocking {
        val response = sqsClient.receiveMessage(ReceiveMessageRequest {
            this.queueUrl = this@SqsMessageConsumer.queueUrl
            this.maxNumberOfMessages = 10
            this.waitTimeSeconds = 1
        })

        response.messages?.forEach { message ->
            try {
                val event = objectMapper.readValue(message.body, PaymentCreatedEvent::class.java)
                val isNewEvent = paymentEventLedgerRepository.record(event)
                if (isNewEvent) {
                    accountAggregateRepository.applyEvent(event)
                    anomalyDetector.detect(event)
                    log.info("Processed event: paymentId={}", event.paymentId)
                }
                sqsClient.deleteMessage(DeleteMessageRequest {
                    this.queueUrl = this@SqsMessageConsumer.queueUrl
                    this.receiptHandle = message.receiptHandle
                })
            } catch (e: Exception) {
                log.error("Failed to process message: {}", message.body, e)
                // Don't delete on failure — let SQS redeliver, eventually to DLQ
            }
        }
    }
}