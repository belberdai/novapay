package dev.novapay.payments.outbox;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", length = 32, nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "poisoned_at")
    private Instant poisonedAt;

    protected OutboxEvent() { }

    public static OutboxEvent create(String aggregateType, UUID aggregateId,
                                  String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.createdAt = Instant.now();
        event.publishedAt = null;
        event.status = OutboxEventStatus.PENDING;
        event.publishAttempts = 0;
        return event;
    }

    public void markPublished(Instant now) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void recordPublishAttempt() {
        this.publishAttempts++;
    }

    public void markPoisoned(Instant now) {
        this.status = OutboxEventStatus.POISONED;
        this.poisonedAt = now;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getPublishAttempts() {
        return publishAttempts;
    }

    public Instant getPoisonedAt() {
        return poisonedAt;
    }

    public Long getId() {  return id; }

    public String getAggregateType() { return aggregateType; }

    public UUID getAggregateId() { return aggregateId; }

    public String getEventType() { return eventType; }

    public String getPayload() { return payload; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getPublishedAt() { return publishedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutboxEvent other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return 31;
    }
}