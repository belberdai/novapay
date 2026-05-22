package dev.novapay.analytics

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.model.*
import kotlinx.coroutines.runBlocking

/**
 * Creates the DynamoDB tables this service depends on.
 *
 * Tests call this in @BeforeEach (or once via @BeforeAll) so the schema
 * exists before any business code runs.
 *
 * Idempotent: if the table already exists, the creation is silently skipped.
 */
fun createTablesIfMissing(client: DynamoDbClient) = runBlocking {
    val existing = client.listTables(ListTablesRequest {}).tableNames.orEmpty().toSet()

    if ("payment_event_ledger" !in existing) {
        client.createTable(CreateTableRequest {
            tableName = "payment_event_ledger"
            attributeDefinitions = listOf(
                AttributeDefinition { attributeName = "paymentId"; attributeType = ScalarAttributeType.S },
                AttributeDefinition { attributeName = "occurredAt"; attributeType = ScalarAttributeType.S },
            )
            keySchema = listOf(
                KeySchemaElement { attributeName = "paymentId"; keyType = KeyType.Hash },
                KeySchemaElement { attributeName = "occurredAt"; keyType = KeyType.Range },
            )
            billingMode = BillingMode.PayPerRequest
        })
    }

    if ("account_aggregates" !in existing) {
        client.createTable(CreateTableRequest {
            tableName = "account_aggregates"
            attributeDefinitions = listOf(
                AttributeDefinition { attributeName = "accountId"; attributeType = ScalarAttributeType.S },
            )
            keySchema = listOf(
                KeySchemaElement { attributeName = "accountId"; keyType = KeyType.Hash },
            )
            billingMode = BillingMode.PayPerRequest
        })
    }
}