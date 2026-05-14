package dev.novapay.payments.idempotency;

/**
 * Thrown when a request arrives with an idempotency key that was previously used
 * with a DIFFERENT request body. This is a client bug — they reused a key for
 * something that isn't actually the same operation.
 * <p>
 * Maps to HTTP 422 Unprocessable Entity at the controller layer.
 */
public class IdempotencyMismatchException extends RuntimeException {

    public IdempotencyMismatchException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey +
                "' was previously used with a different request body");
    }
}