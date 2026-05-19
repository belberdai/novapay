package dev.novapay.analytics.consumer

import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import dev.novapay.analytics.event.PaymentCreatedEvent
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
                log.info("Received: {}", event)
            } catch (e: Exception) {
                log.error("Failed to parse message body: {}", message.body, e)
            }
            // TODO: delete from queue after processing
        }
    }
}