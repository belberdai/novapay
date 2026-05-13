package dev.novapay.payments.payment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_state_transition")
public class PaymentStateTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "from_state")
    @Enumerated(EnumType.STRING)
    private PaymentStatus fromState;

    @Column(name = "to_state", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus toState;

    @Column(name = "reason", length = 256)
    private String reason;

    @Column(name = "actor", length = 64, nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PaymentStateTransition() { }

    public static PaymentStateTransition recordTransition(UUID paymentId, PaymentStatus from, PaymentStatus to, String reason) {
        PaymentStateTransition transition = new PaymentStateTransition();
        transition.paymentId = paymentId;
        transition.fromState = from;
        transition.toState = to;
        transition.reason = reason;
        transition.actor = "system";
        transition.occurredAt = Instant.now();
        return transition;
    }

    public Long getId() { return id; }

    public UUID getPaymentId() { return paymentId; }

    public PaymentStatus getFromState() { return fromState; }

    public PaymentStatus getToState() { return toState;}

    public String getReason() { return reason; }

    public String getActor() { return actor; }

    public Instant getOccurredAt() { return occurredAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentStateTransition other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return 31;
    }
}