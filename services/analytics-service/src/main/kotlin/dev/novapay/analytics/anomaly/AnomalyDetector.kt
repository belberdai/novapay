package dev.novapay.analytics.anomaly

import dev.novapay.analytics.aggregation.AccountAggregateRepository
import dev.novapay.analytics.event.PaymentCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Detects anomalies in payment events.
 *
 * Current rules:
 *   - VELOCITY_THRESHOLD_EXCEEDED: triggered when an account's
 *     transactionsSent exceeds the configured threshold.
 *
 * Each call to detect() runs every rule. Triggered rules write a flag
 * via AnomalyFlagRepository.
 *
 * Note: rules currently read the aggregate state AFTER it's been updated
 * for the current event. This means the threshold check is post-update.
 * A threshold of 5 triggers on the 6th transaction, not the 5th.
 */
@Component
class AnomalyDetector(
    private val aggregateRepository: AccountAggregateRepository,
    private val anomalyFlagRepository: AnomalyFlagRepository,
    private val clock: Clock,
    @Value($$"${anomaly.velocity-threshold:5}") private val velocityThreshold: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun detect(event: PaymentCreatedEvent) {
        checkVelocity(event)
        // future rules go here
    }

    private suspend fun checkVelocity(event: PaymentCreatedEvent) {
        val aggregate = aggregateRepository.findByAccountId(event.sourceAccountId)
            ?: return

        if (aggregate.transactionsSent > velocityThreshold) {
            anomalyFlagRepository.record(AnomalyFlag(
                accountId = event.sourceAccountId,
                flaggedAt = Instant.now(clock),
                anomalyType = "VELOCITY_THRESHOLD_EXCEEDED",
                triggerPaymentId = event.paymentId,
                details = "Account has sent ${aggregate.transactionsSent} transactions, " +
                        "exceeding threshold of $velocityThreshold",
            ))
        }
    }
}