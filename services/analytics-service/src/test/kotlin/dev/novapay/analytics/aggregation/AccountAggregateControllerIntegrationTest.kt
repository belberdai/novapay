package dev.novapay.analytics.aggregation

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import dev.novapay.analytics.TestEndpointConfiguration
import dev.novapay.analytics.TestcontainersConfiguration
import dev.novapay.analytics.createTablesIfMissing
import dev.novapay.analytics.event.PaymentCreatedEvent
import dev.novapay.analytics.event.PaymentStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestcontainersConfiguration::class, TestEndpointConfiguration::class)
class AccountAggregateControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dynamoDbClient: DynamoDbClient

    @Autowired
    private lateinit var aggregateRepository: AccountAggregateRepository

    @BeforeEach
    fun setup() {
        createTablesIfMissing(dynamoDbClient)
    }

    @Test
    fun `GET returns 200 with aggregate when account has activity`(): Unit = runBlocking {
        val accountId = UUID.randomUUID()
        val event = PaymentCreatedEvent(
            eventType = "PaymentCreated",
            paymentId = UUID.randomUUID(),
            sourceAccountId = accountId,
            destinationAccountId = UUID.randomUUID(),
            amountCents = 2500L,
            currency = "CAD",
            status = PaymentStatus.PENDING,
            occurredAt = Instant.now(),
        )
        aggregateRepository.applyEvent(event)

        mockMvc.get("/analytics/accounts/$accountId")
            .andExpect {
                status { isOk() }
                jsonPath("$.accountId") { value(accountId.toString()) }
                jsonPath("$.transactionsSent") { value(1) }
                jsonPath("$.totalCentsSent") { value(2500) }
            }
    }

    @Test
    fun `GET returns 404 when account has no activity`() {
        val nonexistentAccountId = UUID.randomUUID()

        mockMvc.get("/analytics/accounts/$nonexistentAccountId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("account_not_found") }
            }
    }

    @Test
    fun `GET returns 400 when accountId is not a valid UUID`() {
        mockMvc.get("/analytics/accounts/not-a-uuid")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("invalid_parameter") }
            }
    }
}