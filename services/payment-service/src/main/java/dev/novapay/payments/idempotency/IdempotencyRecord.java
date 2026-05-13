package dev.novapay.payments.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "request_fingerprint", length = 64, nullable = false)
    private String requestFingerprint;

    @Column(name = "response_body",  columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String responseBody;

    @Column(name = "response_status", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int responseStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected IdempotencyRecord() {}

    public static IdempotencyRecord create(String key, UUID paymentId, String fingerprint,
                                  String responseBody, int responseStatus) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.idempotencyKey = key;
        record.paymentId = paymentId;
        record.requestFingerprint = fingerprint;
        record.responseBody = responseBody;
        record.responseStatus = responseStatus;
        record.createdAt = Instant.now();
        record.expiresAt = null;
        return record;
    }

    public String getIdempotencyKey() { return idempotencyKey; }

    public UUID getPaymentId() { return paymentId; }

    public String getRequestFingerprint() { return requestFingerprint; }

    public String getResponseBody() { return responseBody; }

    public int getResponseStatus() { return responseStatus; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getExpiresAt() { return expiresAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyRecord other)) return false;
        return idempotencyKey != null && idempotencyKey.equals(other.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return 31;
    }
}