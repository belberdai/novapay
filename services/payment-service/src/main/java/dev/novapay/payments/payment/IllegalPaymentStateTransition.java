package dev.novapay.payments.payment;

public class IllegalPaymentStateTransition extends RuntimeException {

    public IllegalPaymentStateTransition(PaymentStatus from, PaymentStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}