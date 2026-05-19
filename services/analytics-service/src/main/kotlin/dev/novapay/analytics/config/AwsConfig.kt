package dev.novapay.analytics.config

import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AwsConfig {

    @Bean
    fun sqsClient(
        @Value("\${aws.sqs.endpoint:}") endpoint: String,
        @Value("\${aws.region:us-east-1}") region: String,
        @Value("\${aws.access-key-id:test}") accessKey: String,
        @Value("\${aws.secret-access-key:test}") secretKey: String,
    ): SqsClient = runBlocking {
        SqsClient {
            this.region = region
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = accessKey
                this.secretAccessKey = secretKey
            }
            if (endpoint.isNotBlank()) {
                this.endpointUrl = Url.parse(endpoint)
            }
        }
    }
}