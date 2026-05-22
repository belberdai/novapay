package dev.novapay.analytics.aggregation

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import dev.novapay.analytics.TestEndpointConfiguration
import dev.novapay.analytics.TestcontainersConfiguration
import dev.novapay.analytics.event.PaymentCreatedEvent
import dev.novapay.analytics.event.PaymentStatus
import dev.novapay.analytics.ledger.PaymentEventLedgerRepository
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(TestcontainersConfiguration::class, TestEndpointConfiguration::class)
class AccountAggregateIntegrationTest {

    @Autowired
    private lateinit var dynamoDbClient: DynamoDbClient

    @Autowired
    private lateinit var ledgerRepository: PaymentEventLedgerRepository

    @Autowired
    private lateinit var aggregateRepository: AccountAggregateRepository

    @BeforeEach
    fun setup() {
        createTablesIfMissing(dynamoDbClient)
    }

    @Test
    fun `ledger rejects duplicate events with same paymentId and occurredAt`(): Unit = runBlocking {
        val event = sampleEvent()

        val firstWrite = ledgerRepository.record(event)
        val secondWrite = ledgerRepository.record(event)

        assertThat(firstWrite).isTrue()
        assertThat(secondWrite).isFalse()
    }

    @Test
    fun `aggregate counts increment atomically across multiple events`(): Unit = runBlocking {
        val sourceAccount = UUID.randomUUID()
        val destAccount = UUID.randomUUID()

        // three events from source to dest, different paymentIds (no dedup)
        repeat(3) { i ->
            val event = PaymentCreatedEvent(
                eventType = "PaymentCreated",
                paymentId = UUID.randomUUID(),
                sourceAccountId = sourceAccount,
                destinationAccountId = destAccount,
                amountCents = 1000L,
                currency = "CAD",
                status = PaymentStatus.PENDING,
                occurredAt = Instant.now().plusSeconds(i.toLong()),
            )
            aggregateRepository.applyEvent(event)
        }

        val sourceAggregate = aggregateRepository.findByAccountId(sourceAccount)
        val destAggregate = aggregateRepository.findByAccountId(destAccount)

        assertThat(sourceAggregate).isNotNull
        assertThat(sourceAggregate!!.transactionsSent).isEqualTo(3L)
        assertThat(sourceAggregate.totalCentsSent).isEqualTo(3000L)
        assertThat(sourceAggregate.transactionsReceived).isEqualTo(0L)

        assertThat(destAggregate).isNotNull
        assertThat(destAggregate!!.transactionsReceived).isEqualTo(3L)
        assertThat(destAggregate.totalCentsReceived).isEqualTo(3000L)
        assertThat(destAggregate.transactionsSent).isEqualTo(0L)
    }

    private fun sampleEvent(): PaymentCreatedEvent = PaymentCreatedEvent(
        eventType = "PaymentCreated",
        paymentId = UUID.randomUUID(),
        sourceAccountId = UUID.randomUUID(),
        destinationAccountId = UUID.randomUUID(),
        amountCents = 1500L,
        currency = "CAD",
        status = PaymentStatus.PENDING,
        occurredAt = Instant.now(),
    )

    private fun createTablesIfMissing(client: DynamoDbClient) {
        dev.novapay.analytics.createTablesIfMissing(client)
    }
}