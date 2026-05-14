package dev.novapay.payments.payment;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment {
    /**
     * DESIGN NOTE: This class has no setters. State changes are only allowed through
     * behavior methods (validate(), markProcessing(), complete(), fail()). This ensures
     * the state machine rules are always enforced. The service cannot bypass them by
     * calling a setStatus() method. State transitions are centralized, validated, and safe.
     */

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "currency", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    @Column(name = "description", length = 256)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() { }

    public static Payment create(UUID sourceAccountId,
                                 UUID destinationAccountId,
                                 long amountCents,
                                 String currency,
                                 String idempotencyKey,
                                 String description,
                                 Instant now) {
        Payment payment = new Payment();
        payment.sourceAccountId = sourceAccountId;
        payment.destinationAccountId = destinationAccountId;
        payment.amountCents = amountCents;
        payment.currency = currency;
        payment.status = PaymentStatus.PENDING;
        payment.idempotencyKey = idempotencyKey;
        payment.description = description;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    public void validate(Instant now) {
        if (this.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalPaymentStateTransition(this.getStatus(), PaymentStatus.VALIDATED);
        }
        this.status = PaymentStatus.VALIDATED;
        this.updatedAt = now;
    }

    public void markProcessing(Instant now) {
        if (this.getStatus() != PaymentStatus.VALIDATED) {
            throw new IllegalPaymentStateTransition(this.getStatus(), PaymentStatus.PROCESSING);
        }
        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = now;
    }

    public void complete(Instant now) {
        if (this.getStatus() != PaymentStatus.PROCESSING) {
            throw new IllegalPaymentStateTransition(this.getStatus(), PaymentStatus.COMPLETED);
        }
        this.status = PaymentStatus.COMPLETED;
        this.updatedAt = now;
    }

    public void fail(String reason, Instant now) {
        // reason will be saved in PaymentStateTransition
        if (this.getStatus() == PaymentStatus.COMPLETED || status == PaymentStatus.FAILED) {
            throw new IllegalPaymentStateTransition(this.getStatus(), PaymentStatus.FAILED);
        }
        this.status = PaymentStatus.FAILED;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public long getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return 31;
    }
}