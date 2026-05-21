package dev.novapay.payments.outbox;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    POISONED
}