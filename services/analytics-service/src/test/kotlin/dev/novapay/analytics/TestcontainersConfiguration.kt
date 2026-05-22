package dev.novapay.analytics

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    fun localStackContainer(): LocalStackContainer =
        LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
            .withServices(
                LocalStackContainer.Service.SQS,
                LocalStackContainer.Service.DYNAMODB,
            )
}