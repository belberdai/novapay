package dev.novapay.payments.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.novapay.payments.payment.dto.CreatePaymentRequest;
import dev.novapay.payments.payment.dto.PaymentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/**
 * REST endpoints for payment operations.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Validated
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new payment.
     * <p>
     * Idempotency: the client MUST send an Idempotency-Key header. Duplicate
     * requests with the same key + same body return the original 201 response
     * (the call is safe to retry). Same key + different body returns 422
     * via IdempotencyMismatchException → handler.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key header is required")
            @Size(max = 128, message = "Idempotency-Key must be 128 characters or fewer")
            String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        String fingerprintInput = serializeForFingerprint(request);
        Payment payment = paymentService.createPayment(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amountCents(),
                request.currency(),
                request.description(),
                idempotencyKey,
                fingerprintInput
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PaymentResponse.from(payment));
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable UUID id) {
        Payment payment = paymentService.getPayment(id);
        return PaymentResponse.from(payment);
    }

    @GetMapping
    public Page<PaymentResponse> listPayments(Pageable pageable) {
        // Pageable comes from query params: ?page=0&size=20&sort=createdAt,desc
        // Spring Data wires this up automatically when the type is in the method signature.
        return paymentService.listPayments(pageable)
                .map(PaymentResponse::from);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GlobalExceptionHandler.ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = ex.getName() + ": expected " +
                (ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valid value") +
                ", got '" + ex.getValue() + "'";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(GlobalExceptionHandler.ErrorResponse.of("invalid_parameter", "Invalid parameter format",
                        java.util.List.of(detail)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GlobalExceptionHandler.ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(GlobalExceptionHandler.ErrorResponse.of("malformed_request", "Request body could not be parsed"));
    }

    private String serializeForFingerprint(CreatePaymentRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize request for fingerprint", e);
        }
    }
}