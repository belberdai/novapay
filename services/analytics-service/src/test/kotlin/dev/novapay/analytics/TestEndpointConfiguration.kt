package dev.novapay.analytics

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.sqs.SqsClient
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.testcontainers.containers.localstack.LocalStackContainer

/**
 * Overrides the AWS client beans for tests, pointing them at the
Testcontainers LocalStack instance instead of the configured localhost:4566.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestEndpointConfiguration {

    @Bean
    @Primary
    fun testSqsClient(localStack: LocalStackContainer): SqsClient = runBlocking {
        SqsClient {
            this.region = localStack.region
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = localStack.accessKey
                this.secretAccessKey = localStack.secretKey
            }
            // LocalStack's port is randomly assigned.
            // Spring needs to learn the endpoint dynamically.
            this.endpointUrl = Url.parse(localStack.getEndpointOverride(
                LocalStackContainer.Service.SQS).toString())
        }
    }

    @Bean
    @Primary
    fun testDynamoDbClient(localStack: LocalStackContainer): DynamoDbClient = runBlocking {
        DynamoDbClient {
            this.region = localStack.region
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = localStack.accessKey
                this.secretAccessKey = localStack.secretKey
            }
            this.endpointUrl = Url.parse(localStack.getEndpointOverride(
                LocalStackContainer.Service.DYNAMODB).toString())
        }
    }
}