package dev.novapay.analytics.consumer

import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
// Constructor injection via primary constructor
class SqsMessageConsumer(
    private val sqsClient: SqsClient,
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
            log.info("Received SQS message: id={}, body={}", message.messageId, message.body)
            // TODO: delete from queue after processing
        }
    }
}