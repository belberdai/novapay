package dev.novapay.payments.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, String> { }