package dev.novapay.analytics.event

enum class PaymentStatus {
    PENDING,
    VALIDATED,
    PROCESSING,
    COMPLETED,
    FAILED
}