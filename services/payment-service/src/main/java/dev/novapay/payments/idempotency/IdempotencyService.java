package dev.novapay.payments.idempotency;

import dev.novapay.payments.payment.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class IdempotencyService {

    private final PaymentRepository paymentRepository;

    public IdempotencyService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment resolveExisting(IdempotencyRecord existing,
                                    String currentFingerprint,
                                    String idempotencyKey) {
        if (!existing.getRequestFingerprint().equals(currentFingerprint)) {
            throw new IdempotencyMismatchException(idempotencyKey);
        }
        return paymentRepository.findById(existing.getPaymentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record points to missing payment " + existing.getPaymentId()
                ));
    }

    public String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
